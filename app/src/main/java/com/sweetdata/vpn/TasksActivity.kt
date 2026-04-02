package com.sweetdata.vpn

import android.content.Context
import android.os.Bundle
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
    private var mInterstitialAd: InterstitialAd? = null
    private var tvAdProgress: TextView? = null
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        tvAdProgress = findViewById(R.id.tvAdProgress)
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        
        // Input Fields
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etMpesaMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)

        loadNextAd()

        // --- ADS LOGIC (6 ADS = 2 HOURS) ---
        btnWatchAd?.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        processAdReward()
                        loadNextAd() 
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                Toast.makeText(this, "Ad loading...", Toast.LENGTH_SHORT).show()
                loadNextAd()
            }
        }

        // --- SUBMISSION LOGIC (MANUAL APPROVAL) ---
        btnSubmit?.setOnClickListener {
            val proof = etMpesaMsg?.text?.toString()?.trim() ?: ""
            val user = auth.currentUser ?: return@setOnClickListener

            if (proof.isEmpty()) {
                Toast.makeText(this, "Please paste your payment proof", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = HashMap<String, Any>()
            data["email"] = user.email ?: "No Email"
            data["uid"] = user.uid
            data["title"] = etTitle?.text.toString()
            data["description"] = etDesc?.text.toString()
            data["link"] = etLink?.text.toString()
            data["proof"] = proof
            data["status"] = "pending"
            data["type"] = "TASK_OR_SUB"

            database.child("task_promos").push().setValue(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Sent to Admin for 24H Activation!", Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun processAdReward() {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0) + 1

        if (count >= 6) {
            val twoHoursMs = 120 * 60 * 1000L
            val now = System.currentTimeMillis()
            val currentExp = prefs.getLong("expiry_time", now)
            val newExp = (if (currentExp > now) currentExp else now) + twoHoursMs
            
            prefs.edit().putLong("expiry_time", newExp).putInt("ad_count", 0).apply()
            auth.currentUser?.uid?.let { database.child("users").child(it).child("expiry_time").setValue(newExp) }
            Toast.makeText(this, "✅ 2 Hours Added!", Toast.LENGTH_SHORT).show()
            updateAdProgressUI(0)
        } else {
            prefs.edit().putInt("ad_count", count).apply()
            updateAdProgressUI(count)
        }
    }

    private fun updateAdProgressUI(count: Int) {
        tvAdProgress?.text = "Progress: $count/6 Ads"
    }

    private fun loadNextAd() {
        InterstitialAd.load(this, INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
        })
    }
}
