package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class TasksActivity : AppCompatActivity() {

    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PREFS_NAME = "SweetDataPrefs"
    private var mInterstitialAd: InterstitialAd? = null
    private var tvAdProgress: TextView? = null
    private val auth = FirebaseAuth.getInstance()
    
    // Updated with direct URL to ensure connection
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        tvAdProgress = findViewById(R.id.tvAdProgress)
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        loadNextAd()
        updateAdProgressUI()

        btnWatchAd?.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        handleAdReward()
                        loadNextAd() 
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        mInterstitialAd = null
                        loadNextAd()
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                // If ad is null, we FORCE a reload immediately when they click
                Toast.makeText(this, "Refreshing Ad... Try again in 3 seconds", Toast.LENGTH_SHORT).show()
                loadNextAd()
            }
        }

        btnSubmitTask?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: ""
            val link = etLink?.text?.toString()?.trim() ?: ""
            val msg = etPaymentMsg?.text?.toString()?.trim() ?: ""

            if (title.isEmpty() || link.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else {
                val promoData = HashMap<String, Any>()
                promoData["email"] = auth.currentUser?.email ?: "User"
                promoData["title"] = title
                promoData["link"] = link
                promoData["proof"] = msg
                promoData["status"] = "pending"

                database.child("task_promos").push().setValue(promoData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Submitted successfully!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Firebase Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun handleAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("ad_count", 0) + 1

        if (currentCount >= 6) {
            val rewardMillis = 120 * 60 * 1000L
            val currentExpiry = prefs.getLong("expiry_time", System.currentTimeMillis())
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            val newExpiryTime = baseTime + rewardMillis

            prefs.edit().putLong("expiry_time", newExpiryTime).putInt("ad_count", 0).apply()

            auth.currentUser?.uid?.let { uid ->
                database.child("users").child(uid).child("expiry_time").setValue(newExpiryTime)
            }
            Toast.makeText(this, "✅ 2 Hours Added!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            prefs.edit().putInt("ad_count", currentCount).apply()
            updateAdProgressUI()
        }
    }

    private fun updateAdProgressUI() {
        val count = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("ad_count", 0)
        runOnUiThread {
            tvAdProgress?.text = if (mInterstitialAd != null) "AD READY: $count/6" else "AD LOADING: $count/6..."
        }
    }

    private fun loadNextAd() {
        if (mInterstitialAd != null) return
        
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                mInterstitialAd = ad
                updateAdProgressUI()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                mInterstitialAd = null
                updateAdProgressUI()
                // Retry every 7 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ loadNextAd() }, 7000)
            }
        })
    }
}
