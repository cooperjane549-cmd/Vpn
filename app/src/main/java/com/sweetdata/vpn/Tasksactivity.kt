package com.sweetdata.vpn

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton

class TasksActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        loadRewardedAd()

        // Link the button to the XML ID
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        btnWatchAd.setOnClickListener {
            showRewardedAd()
        }

        findViewById<MaterialButton>(R.id.btnCreateTask).setOnClickListener {
            Toast.makeText(this, "Redirecting to Admin...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        // Test ID for Rewarded Ads
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
            })
    }

    private fun showRewardedAd() {
        rewardedAd?.let { ad ->
            ad.show(this) { rewardItem: RewardItem ->
                Toast.makeText(this, "Earned ${rewardItem.amount} MB", Toast.LENGTH_LONG).show()
                loadRewardedAd()
            }
        } ?: run {
            Toast.makeText(this, "Ad not ready yet. Please wait...", Toast.LENGTH_SHORT).show()
        }
    }
}
