package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton

class TasksActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // Load Rewarded Ad (5MB Logic)
        loadRewarded()

        findViewById<MaterialButton>(R.id.btnWatchAd).setOnClickListener {
            rewardedAd?.show(this) { _ -> 
                Toast.makeText(this, "5MB Credited!", Toast.LENGTH_SHORT).show()
                // Update local wallet logic here
            }
        }

        // Paid Task Creation (KSh 450)
        findViewById<MaterialButton>(R.id.btnCreateTask).setOnClickListener {
            val wpIntent = Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://wa.me/+254789574046?text=I want to pay KSh 450 to launch a social task."))
            startActivity(wpIntent)
        }
    }

    private fun loadRewarded() {
        // Replace with your specific Rewarded Ad ID if different
        com.google.android.gms.ads.rewarded.RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", 
            com.google.android.gms.ads.AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            })
    }
}

