package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import java.io.IOException
import java.util.*

class TasksActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    
    private val client = OkHttpClient()
    private val PREFS_NAME = "SweetDataPrefs"
    
    // Ad IDs
    private val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917" // Test ID (Replace with yours later)
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920" // Your New ID

    // Admin Credentials
    private val botToken = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val adminChatId = "6847108451"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // Initial Ad Pull (Pre-loader)
        pullAdsFromServer()
        updateAdProgressUI()

        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayForTask = findViewById<MaterialButton>(R.id.btnPayForTask)
        val cardTaskPayment = findViewById<MaterialCardView>(R.id.cardTaskPayment)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        // 1. WATCH AD LOGIC (Using Interstitial as per your request)
        btnWatchAd.setOnClickListener {
            triggerAdSequence()
        }

        // 2. CREATE TASK - VALIDATION
        btnPayForTask.setOnClickListener {
            if (etTitle.text.isBlank() || etDesc.text.isBlank() || etLink.text.isBlank()) {
                Toast.makeText(this, "Please fill all fields first!", Toast.LENGTH_SHORT).show()
            } else {
                cardTaskPayment.visibility = View.VISIBLE
                Toast.makeText(this, "Till 3043489 copied. Paste message below.", Toast.LENGTH_LONG).show()
            }
        }

        // 3. SUBMIT TO TELEGRAM
        btnSubmitTask.setOnClickListener {
            val msg = etPaymentMsg.text.toString().trim()
            if (msg.length < 15) {
                Toast.makeText(this, "Please paste a valid M-Pesa/PayPal message", Toast.LENGTH_SHORT).show()
            } else {
                val taskData = "🆕 NEW TASK REQUEST\nTitle: ${etTitle.text}\nLink: ${etLink.text}\nProof: $msg"
                notifyAdminViaTelegram(taskData)
                Toast.makeText(this, "Submitted! Admin will verify.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        findViewById<TextView>(R.id.tvSupport).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626")))
        }
    }

    // THE PULLER: Always keeps ads ready in background
    private fun pullAdsFromServer() {
        val adRequest = AdRequest.Builder().build()

        // Pull Interstitial
        InterstitialAd.load(this, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { interstitialAd = null }
        })

        // Pull Rewarded (Secondary buffer)
        RewardedAd.load(this, REWARDED_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
        })
    }

    private fun triggerAdSequence() {
        if (interstitialAd != null) {
            interstitialAd?.show(this)
            // Immediately start pulling the NEXT ad while user watches this one
            interstitialAd = null 
            handleAdReward() // Count the view
            pullAdsFromServer() 
        } else if (rewardedAd != null) {
            rewardedAd?.show(this) { 
                handleAdReward()
                pullAdsFromServer()
            }
            rewardedAd = null
        } else {
            Toast.makeText(this, "Pulling new ads... please try again in 5s", Toast.LENGTH_SHORT).show()
            pullAdsFromServer() // Force a retry pull
        }
    }

    private fun handleAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("last_ad_day", -1)
        var count = prefs.getInt("ad_count", 0)

        if (today != lastDay) { count = 0; prefs.edit().putInt("last_ad_day", today).apply() }

        count++
        if (count >= 6) {
            val threeHours = 3 * 60 * 60 * 1000L
            prefs.edit().apply {
                putLong("expiry_time", System.currentTimeMillis() + threeHours)
                putInt("ad_count", 0)
                putBoolean("force_vpn", true)
                apply()
            }
            Toast.makeText(this, "3 Hours UNLIMITED Unlocked!", Toast.LENGTH_LONG).show()
            startService(Intent(this, MyVpnService::class.java)) 
        } else {
            prefs.edit().putInt("ad_count", count).apply()
        }
        updateAdProgressUI()
    }

    private fun updateAdProgressUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0)
        findViewById<TextView>(R.id.tvAdProgress).text = "Progress: $count/6 Ads Watched"
    }

    private fun notifyAdminViaTelegram(message: String) {
        val body = FormBody.Builder().add("chat_id", adminChatId).add("text", message).build()
        val request = Request.Builder().url("https://api.telegram.org/bot$botToken/sendMessage").post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
