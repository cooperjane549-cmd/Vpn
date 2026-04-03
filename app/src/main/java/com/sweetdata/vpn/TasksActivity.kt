package com.sweetdata.vpn

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class TasksActivity : AppCompatActivity() {

    private lateinit var tvProgress: TextView
    private lateinit var btnWatch: Button
    private lateinit var tvLimitMsg: TextView
    
    private var adsWatched = 0
    private var mInterstitialAd: InterstitialAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        tvProgress = findViewById(R.id.tvAdProgress)
        btnWatch = findViewById(R.id.btnWatchAd)
        
        // Add a TextView in your XML for the limit message if you haven't yet
        // tvLimitMsg = findViewById(R.id.tvLimitMessage) 

        MobileAds.initialize(this)
        loadInterstitial()
        checkDailyLimit()

        btnWatch.setOnClickListener {
            if (mInterstitialAd != null) {
                showAd()
            } else {
                Toast.makeText(this, "Ad loading, please try again...", Toast.LENGTH_SHORT).show()
                loadInterstitial()
            }
        }
    }

    private fun checkDailyLimit() {
        val userId = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        database.child("users").child(userId).child("last_ad_claim_date")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lastDate = snapshot.getValue(String::class.java)
                    if (lastDate == today) {
                        // Limit reached
                        btnWatch.isEnabled = false
                        btnWatch.text = "DAILY LIMIT REACHED"
                        Toast.makeText(this@TasksActivity, "You already used your free 2hrs today!", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    mInterstitialAd = null
                }
            })
    }

    private fun showAd() {
        mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adsWatched++
                updateUI()
                if (adsWatched >= 6) {
                    grantTwoHourReward()
                } else {
                    loadInterstitial() // Load next ad
                }
            }
        }
        mInterstitialAd?.show(this)
    }

    private fun updateUI() {
        tvProgress.text = "Progress: $adsWatched / 6"
    }

    private fun grantTwoHourReward() {
        val userId = auth.currentUser?.uid ?: return
        val currentTime = System.currentTimeMillis()
        val twoHoursInMs = 2 * 60 * 60 * 1000
        val newExpiry = currentTime + twoHoursInMs
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val updates = mapOf(
            "expiry_time" to newExpiry,
            "last_ad_claim_date" to today
        )

        database.child("users").child(userId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "2 Hours Free Access Activated!", Toast.LENGTH_LONG).show()
                finish() // Go back to MainActivity
            }
    }
}
