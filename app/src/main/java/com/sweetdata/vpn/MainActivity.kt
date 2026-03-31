package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
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
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 1. Firebase Manual Config (Kept exactly as you have it)
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1085998005937:android:8451888af22059a9942c90")
            .setProjectId("sweetdatavpn")
            .setApiKey("AIzaSyB...") // Use your full key here
            .setDatabaseUrl("https://sweetdatavpn-default-rtdb.firebaseio.com")
            .build()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, options)
        }

        setContentView(R.layout.activity_main)

        // 2. Initialize Firebase & Ads
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        MobileAds.initialize(this) {}
        loadInterstitial()
        signInSilently()

        // 3. Link UI (Matching your activity_main.xml)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        updateBalanceUI()

        // 4. Network Toggle Logic (Mapped to Kevin/Sherwin internally)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                // Use the IDs from your XML to set the internal label
                val displayName = if (checkedId == R.id.btnSafaricom) "Kevin" else "Sherwin"
                prefs.edit().putString("selected_network", displayName).apply()
                Toast.makeText(this, "Mode: $displayName", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Connect Button Logic
        btnConnect.setOnClickListener {
            if (isVpnRunning) stopVpn() else checkAccessAndStart()
        }

        // 6. FIXED NAVIGATION - These match your Manifest exactly
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
    }

    private fun checkAccessAndStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)

        if (isSubscribed || System.currentTimeMillis() < expiry) {
            startVpnProcess()
        } else {
            Toast.makeText(this, "No time left! Go to TASKS or STORE", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateBalanceUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)

        if (System.currentTimeMillis() < expiry) {
            val remaining = (expiry - System.currentTimeMillis()) / (60 * 1000)
            tvStatus.text = "ACTIVE"
            tvBalance.text = "${remaining} MIN" // Showing Minutes instead of MB
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        } else {
            tvStatus.text = "DISCONNECTED"
            tvBalance.text = "0 MIN"
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
            val serviceIntent = Intent(this, MyVpnService::class.java)
            startService(serviceIntent)
            isVpnRunning = true
            btnConnect.text = "STOP"
            btnConnect.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            tvStatus.text = "CONNECTED"
        }
    }

    private fun stopVpn() {
        val serviceIntent = Intent(this, MyVpnService::class.java).apply { action = "STOP" }
        startService(serviceIntent)
        isVpnRunning = false
        btnConnect.text = "CONNECT"
        btnConnect.setBackgroundColor(android.graphics.Color.parseColor("#FF0033"))
        updateBalanceUI()
    }

    private fun signInSilently() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FirebaseInstallations.getInstance().id.addOnSuccessListener { id ->
                    deviceId = id
                    database.child("users").child(id).child("last_seen").setValue(System.currentTimeMillis())
                }
            }
        }
    }

    private fun loadInterstitial() {
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
            })
    }

    override fun onResume() {
        super.onResume()
        updateBalanceUI()
    }
}
