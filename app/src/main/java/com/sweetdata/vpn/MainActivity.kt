package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.telephony.TelephonyManager
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvAdCounter: TextView
    private lateinit var btnWatchAd: MaterialButton
    
    // Logic Variables
    private var mInterstitialAd: InterstitialAd? = null
    private var isVpnRunning = false
    private val PREFS_NAME = "SweetDataPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Ads
        MobileAds.initialize(this) {}
        loadInterstitial()

        // Link UI
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvAdCounter = findViewById(R.id.tvMbBalance) // Reusing your balance view for ad count
        btnWatchAd = findViewById(R.id.navTasks) // Reusing task button for the ad trigger

        updateUiState()

        // --- 1. AD WATCH LOGIC (6 Ads = 3 Hours) ---
        btnWatchAd.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        incrementAdCount()
                        loadInterstitial() // Load next one
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                Toast.makeText(this, "Ad not ready. Check internet.", Toast.LENGTH_SHORT).show()
                loadInterstitial()
            }
        }

        // --- 2. CONNECT LOGIC (Kevin/Sherwin) ---
        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpn()
            } else {
                if (hasValidAccess()) {
                    val carrier = autoDetectNetwork()
                    Toast.makeText(this, "Optimizing for $carrier...", Toast.LENGTH_SHORT).show()
                    startVpnProcess()
                } else {
                    Toast.makeText(this, "Unlock access first (Watch 6 Ads or Subscribe)", Toast.LENGTH_LONG).show()
                }
            }
        }

        // --- 3. NAVIGATION (Fixed Crashes) ---
        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            try {
                startActivity(Intent(this, SubscriptionActivity::class.java))
            } catch (e: Exception) {
                Log.e("NavError", "Store Crash: ${e.message}")
            }
        }
    }

    private fun autoDetectNetwork(): String {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val operator = tm.networkOperatorName.lowercase()
            when {
                operator.contains("safaricom") -> "Kevin"
                operator.contains("airtel") -> "Sherwin"
                else -> "Kevin" // Default
            }
        } catch (e: Exception) { "Kevin" }
    }

    private fun hasValidAccess(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        
        return isSubscribed || System.currentTimeMillis() < expiry
    }

    private fun incrementAdCount() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("last_ad_day", -1)
        
        var count = prefs.getInt("ad_count", 0)
        
        // Reset if it's a new day
        if (today != lastDay) {
            count = 0
            prefs.edit().putInt("last_ad_day", today).apply()
        }

        count++
        
        if (count >= 6) {
            val threeHours = 3 * 60 * 60 * 1000L
            prefs.edit().putLong("expiry_time", System.currentTimeMillis() + threeHours).apply()
            prefs.edit().putInt("ad_count", 0).apply() // Reset counter after reward
            Toast.makeText(this, "SUCCESS! 3 Hours Unlocked", Toast.LENGTH_LONG).show()
        } else {
            prefs.edit().putInt("ad_count", count).apply()
        }
        updateUiState()
    }

    private fun updateUiState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0)
        val expiry = prefs.getLong("expiry_time", 0)
        
        tvAdCounter.text = "Ads: $count/6"
        
        if (hasValidAccess()) {
            val remaining = (expiry - System.currentTimeMillis()) / (60 * 1000)
            tvStatus.text = if (prefs.getBoolean("is_subscribed", false)) "PREMIUM ACTIVE" 
                            else "FREE TRIAL: ${remaining}m left"
        } else {
            tvStatus.text = "LOCKED: Watch 6 Ads"
        }
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
            })
    }

    // VPN Boilerplate
    private fun startVpnProcess() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 102) 
        else onActivityResult(102, RESULT_OK, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
            isVpnRunning = true
            btnConnect.text = "STOP TUNNEL"
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply { action = "STOP" })
        isVpnRunning = false
        btnConnect.text = "START TUNNEL"
    }
}
