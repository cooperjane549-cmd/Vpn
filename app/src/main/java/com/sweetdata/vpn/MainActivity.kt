package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvBalance: TextView
    private var mInterstitialAd: InterstitialAd? = null
    
    // Track local state to toggle button
    private var isVpnRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize AdMob for the Interstitial
        MobileAds.initialize(this) {}
        loadInterstitial()

        // 2. Link UI Elements (Aligned with activity_main.xml IDs)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)

        // 3. Setup Navigation
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        
        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        // 4. Connect Button Logic
        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpnService()
            } else {
                prepareAndStartVpn()
            }
        }
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        // Using your specific Interstitial ID
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }
            })
    }

    private fun prepareAndStartVpn() {
        // Show Interstitial Ad before connecting
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            loadInterstitial() // Load next one for later
        }

        // Check VPN Permission
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Android needs to show the "Trust this app" dialog
            startActivityForResult(intent, 0)
        } else {
            // Permission already granted, start immediately
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        startService(intent)
        updateUi(true)
    }

    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        stopService(intent)
        updateUi(false)
    }

    private fun updateUi(running: Boolean) {
        isVpnRunning = running
        if (running) {
            btnConnect.text = "STOP TUNNEL"
            btnConnect.setBackgroundColor(getColor(R.color.matcha_green))
            tvStatus.text = "CONNECTED TO SWEETDATA"
            tvStatus.setTextColor(getColor(R.color.matcha_green))
        } else {
            btnConnect.text = "START TUNNEL"
            btnConnect.setBackgroundColor(getColor(R.color.primary_red))
            tvStatus.text = "SECURE & ANONYMOUS"
            tvStatus.setTextColor(getColor(R.color.text_gray))
        }
    }
}
