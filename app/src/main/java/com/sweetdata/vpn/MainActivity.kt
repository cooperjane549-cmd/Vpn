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
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 2. MANUAL FIREBASE INITIALIZATION (SweetData VPN Project)
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1085998005937:android:8451888af22059a9942c90")
            .setProjectId("sweetdatavpn")
            .setApiKey("AIzaSyB...") // Replace with your actual full key
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

        // 6. Network Selection Logic (Kevin/Sherwin)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val technicalNetwork = if (checkedId == R.id.btnSafaricom) "Safaricom" else "Airtel"
                val displayName = if (checkedId == R.id.btnSafaricom) "Kevin" else "Sherwin"
                
                prefs.edit().putString("selected_network", technicalNetwork).apply()
                Toast.makeText(this, "SweetData: $displayName Selected", Toast.LENGTH_SHORT).show()
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

        // 8. Navigation Buttons (LINKED TO SUBSCRIPTION ACTIVITY)
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            val intent = Intent(this, TasksActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            // UPDATED: Now correctly points to SubscriptionActivity
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }
    }

    // --- ACCESS LOGIC (CHECKS FOR VIP OR TASK TIME) ---

    private fun checkAccessAndStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)

        // If user is VIP or has active Ad-time
        if (isSubscribed || System.currentTimeMillis() < expiry) {
            startVpnProcess()
        } else {
            Toast.makeText(this, "Need Access? Watch Ads or Get VIP in Store", Toast.LENGTH_LONG).show()
            // Optional: Auto-open the subscription page if they have no access
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }
    }

    // --- FIREBASE ANTI-CHEAT LOGIC ---

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

    // --- VPN & UI LOGIC ---

    private fun updateBalanceUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0)
        val expiry = prefs.getLong("expiry_time", 0)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        
        if (isSubscribed) {
            tvBalance.text = "VIP Status: UNLIMITED"
            tvStatus.text = "SWEETDATA VIP ACTIVE"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else if (System.currentTimeMillis() < expiry) {
            val remaining = (expiry - System.currentTimeMillis()) / (60 * 1000)
            tvBalance.text = "Trial: ${remaining} min left"
            tvStatus.text = "READY TO CONNECT"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        } else {
            tvBalance.text = "Ads: $count/6 Watched"
            tvStatus.text = "DISCONNECTED"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
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
        tvStatus.text = "TUNNEL CONNECTED"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun stopVpn() {
        val serviceIntent = Intent(this, MyVpnService::class.java).apply { action = "STOP" }
        startService(serviceIntent)
        isVpnRunning = false
        btnConnect.text = "START TUNNEL"
        updateBalanceUI()
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
        updateBalanceUI() // Refresh whenever user returns to SweetData VPN
    }
}
