package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.installations.FirebaseInstallations
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvBalance: TextView
    private lateinit var toggleNetwork: MaterialButtonToggleGroup
    
    // Logic Variables
    private var mInterstitialAd: InterstitialAd? = null
    private var isVpnRunning = false
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var deviceId: String? = null
    private val PREFS_NAME = "SweetDataPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Splash Screen
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 2. MANUAL FIREBASE INITIALIZATION (Restored)
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1085998005937:android:8451888af22059a9942c90")
            .setProjectId("sweetdatavpn")
            .setApiKey("AIzaSyB..." ) 
            .setDatabaseUrl("https://sweetdatavpn-default-rtdb.firebaseio.com")
            .build()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, options)
        }

        setContentView(R.layout.activity_main)

        // 3. Initialize Firebase Auth & Ads
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        MobileAds.initialize(this) {}
        loadInterstitial()
        signInSilently()

        // 4. Link UI Components
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        // 5. Initial UI Update
        updateBalanceUI()

        // 6. Network Selection Logic (Updated with Kevin/Sherwin naming)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val network = if (checkedId == R.id.btnSafaricom) "Kevin" else "Sherwin"
                prefs.edit().putString("selected_network", network).apply()
                Toast.makeText(this, "Network set to $network", Toast.LENGTH_SHORT).show()
            }
        }

        // 7. Connect Button Logic
        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpn()
            } else {
                checkAccessAndStart()
            }
        }

        // 8. Navigation Buttons (Fixed Intents)
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            // Logic for watching ads to unlock 3 hours
            showRewardAd()
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }
    }

    // --- ACCESS LOGIC (6 ADS = 3 HOURS) ---

    private fun showRewardAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    processAdReward()
                    loadInterstitial()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            Toast.makeText(this, "Ad loading... try again.", Toast.LENGTH_SHORT).show()
            loadInterstitial()
        }
    }

    private fun processAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("last_ad_day", -1)
        var count = prefs.getInt("ad_count", 0)

        if (today != lastDay) { count = 0; prefs.edit().putInt("last_ad_day", today).apply() }
        
        count++
        if (count >= 6) {
            val threeHours = 3 * 60 * 60 * 1000L
            prefs.edit().putLong("expiry_time", System.currentTimeMillis() + threeHours).apply()
            prefs.edit().putInt("ad_count", 0).apply()
            Toast.makeText(this, "3 Hours Access Unlocked!", Toast.LENGTH_LONG).show()
        } else {
            prefs.edit().putInt("ad_count", count).apply()
        }
        updateBalanceUI()
    }

    private fun checkAccessAndStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)

        if (isSubscribed || System.currentTimeMillis() < expiry) {
            startVpnProcess()
        } else {
            Toast.makeText(this, "Please watch 6 ads for 3hrs access", Toast.LENGTH_LONG).show()
        }
    }

    // --- FIREBASE ANTI-CHEAT LOGIC (Untouched) ---

    private fun signInSilently() {
        auth.signInAnonymously().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                fetchDeviceInstallationId()
            } else {
                Log.e("SweetDataAuth", "Sign-in failed: ${task.exception?.message}")
            }
        }
    }

    private fun fetchDeviceInstallationId() {
        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                deviceId = task.result
                deviceId?.let { database.child("users").child(it).child("last_seen").setValue(System.currentTimeMillis()) }
            }
        }
    }

    // --- VPN & UI LOGIC (Updated to show Ad Count) ---

    private fun updateBalanceUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0)
        val expiry = prefs.getLong("expiry_time", 0)
        
        // Show current ad progress on the balance text view
        tvBalance.text = "Ads: $count/6"
        
        if (System.currentTimeMillis() < expiry) {
            val remaining = (expiry - System.currentTimeMillis()) / (60 * 1000)
            tvStatus.text = "TRIAL ACTIVE: ${remaining}m"
        }
    }

    private fun startVpnProcess() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 102)
        } else {
            onActivityResult(102, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val serviceIntent = Intent(this, MyVpnService::class.java)
        startService(serviceIntent)
        isVpnRunning = true
        btnConnect.text = "STOP TUNNEL"
        tvStatus.text = "CONNECTED"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun stopVpn() {
        val serviceIntent = Intent(this, MyVpnService::class.java).apply { action = "STOP" }
        startService(serviceIntent)
        isVpnRunning = false
        btnConnect.text = "START TUNNEL"
        tvStatus.text = "DISCONNECTED"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
            })
    }

    override fun onResume() {
        super.onResume()
        updateBalanceUI()
    }
}
