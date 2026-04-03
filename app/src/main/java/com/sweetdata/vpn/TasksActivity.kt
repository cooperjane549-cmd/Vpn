package com.sweetdata.vpn

import android.content.Context
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
    
    private var adsWatched = 0
    private var mInterstitialAd: InterstitialAd? = null
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val PREFS_NAME = "SweetDataPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        tvProgress = findViewById(R.id.tvAdProgress)
        btnWatch = findViewById(R.id.btnWatchAd)

        // LOAD SAVED PROGRESS
        adsWatched = getSavedAdCount()
        updateUI()

        MobileAds.initialize(this)
        loadInterstitial()
        checkDailyLimit()

        btnWatch.setOnClickListener {
            if (mInterstitialAd != null) {
                showAd()
            } else {
                // If ad isn't ready, disable button and try loading again
                btnWatch.isEnabled = false
                btnWatch.text = "LOADING AD..."
                Toast.makeText(this, "Fetching ad... please wait.", Toast.LENGTH_SHORT).show()
                loadInterstitial()
            }
        }
    }

    // --- SHARED PREFS LOGIC (The Memory) ---
    private fun saveAdCount(count: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("temp_ad_count", count).apply()
    }

    private fun getSavedAdCount(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("temp_ad_count", 0)
    }

    private fun checkDailyLimit() {
        val userId = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        database.child("users").child(userId).child("last_ad_claim_date")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lastDate = snapshot.getValue(String::class.java)
                    if (lastDate == today) {
                        btnWatch.isEnabled = false
                        btnWatch.text = "DAILY LIMIT REACHED"
                        saveAdCount(0) // Clean up any partial progress if they are already locked out
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
                    // Re-enable button if it was stuck in "Loading"
                    if (btnWatch.text == "LOADING AD...") {
                        btnWatch.isEnabled = true
                        btnWatch.text = "WATCH VIDEO AD"
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    mInterstitialAd = null
                    // If it fails, wait 5 seconds and try again automatically
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadInterstitial()
                    }, 5000)
                }
            })
    }

    private fun showAd() {
        mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                mInterstitialAd = null
                adsWatched++
                saveAdCount(adsWatched) // SAVE PROGRESS
                updateUI()
                
                if (adsWatched >= 6) {
                    grantTwoHourReward()
                } else {
                    loadInterstitial() // Pre-load next ad
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                mInterstitialAd = null
                loadInterstitial()
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
                saveAdCount(0) // RESET PROGRESS TO 0 AFTER REWARD
                Toast.makeText(this, "2 Hours Free Access Activated!", Toast.LENGTH_LONG).show()
                finish() 
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: Check internet and try again.", Toast.LENGTH_SHORT).show()
            }
    }
}
