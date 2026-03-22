package com.sweetdata.vpn

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton

class TasksActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        loadRewardedAd()

        findViewById<MaterialButton>(R.id.btnWatchAd).setOnClickListener {
            showAd()
        }

        findViewById<MaterialButton>(R.id.btnCreateTask).setOnClickListener {
            // Logic for KSh 450 task creation
            Toast.makeText(this, "Opening Task Creator...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        // Using Test ID for now - replace with your real Rewarded ID later
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, 
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            })
    }

    private fun showAd() {
        rewardedAd?.show(this) { rewardItem ->
            Toast.makeText(this, "Earned ${rewardItem.amount} MB", Toast.LENGTH_LONG).show()
            loadRewardedAd() // Load next
        } ?: run {
            Toast.makeText(this, "Ad not ready", Toast.LENGTH_SHORT).show()
        }
    }
}
