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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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
    private val database = FirebaseDatabase.getInstance().reference
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Splash Screen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 2. Initialize Firebase & Ads for SweetDataVPN
        auth = FirebaseAuth.getInstance()
        MobileAds.initialize(this) {}
        loadInterstitial()
        signInSilently()

        // 3. Link UI Components
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        // 4. Initial UI Update
        updateBalanceUI()

        // 5. Network Selection Logic (Safaricom vs Airtel)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
                val network = if (checkedId == R.id.btnSafaricom) "Safaricom" else "Airtel"
                prefs.edit().putString("selected_network", network).apply()
                Toast.makeText(this, "Network set to $network", Toast.LENGTH_SHORT).show()
            }
        }

        // 6. Connect Button Logic
        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpn()
            } else {
                checkBalanceAndStart()
            }
        }

        // 7. Navigation Buttons
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
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
                Log.d("SweetDataID", "Device verified: $deviceId")
            }
        }
    }

    // --- VPN & AD LOGIC ---

    private fun updateBalanceUI() {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val balance = prefs.getInt("mb_balance", 0)
        tvBalance.text = "$balance MB"
    }

    private fun checkBalanceAndStart() {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val balance = prefs.getInt("mb_balance", 0)

        if (balance <= 0) {
            Toast.makeText(this, "Insufficient MBs! Please complete tasks.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, TasksActivity::class.java))
        } else {
            showAdAndConnect()
        }
    }

    private fun showAdAndConnect() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            loadInterstitial() // Reload for next use
        }
        
        // Prepare VPN
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
        tvStatus.text = "CONNECTED & TUNNELING"
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
        // Replace with your real SweetDataVPN Ad Unit ID before publishing
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
            })
    }

    override fun onResume() {
        super.onResume()
        updateBalanceUI() // Refresh MB balance when returning to home
    }
}
