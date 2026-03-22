package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: MaterialButton
    private lateinit var tvBalance: TextView
    private var mInterstitialAd: InterstitialAd? = null
    private var isVpnActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdMob
        MobileAds.initialize(this) {}
        loadInterstitial()

        btnConnect = findViewById(R.id.btnConnect)
        tvBalance = findViewById(R.id.tvMbBalance)

        // Setup Navigation
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.navStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        btnConnect.setOnClickListener {
            if (isVpnActive) stopVpn() else startVpnFlow()
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

    private fun startVpnFlow() {
        mInterstitialAd?.show(this)
        loadInterstitial() // Reload for next time
        
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 0) 
        else onActivityResult(0, RESULT_OK, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
            updateUi(true)
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, MyVpnService::class.java))
        updateUi(false)
    }

    private fun updateUi(active: Boolean) {
        isVpnActive = active
        btnConnect.text = if (active) "STOP TUNNEL" else "START TUNNEL"
        btnConnect.setBackgroundColor(if (active) getColor(R.color.matcha_green) else getColor(R.color.primary_red))
    }
}
