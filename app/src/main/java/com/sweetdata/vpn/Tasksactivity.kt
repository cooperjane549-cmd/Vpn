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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import java.io.IOException
import java.util.*

class TasksActivity : AppCompatActivity() {

    private var rewardedAd: RewardedAd? = null
    private val client = OkHttpClient()
    private val PREFS_NAME = "SweetDataPrefs"
    
    // Admin Credentials
    private val botToken = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val adminChatId = "6847108451"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        loadRewardedAd()
        updateAdProgressUI()

        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayForTask = findViewById<MaterialButton>(R.id.btnPayForTask)
        val cardTaskPayment = findViewById<MaterialCardView>(R.id.cardTaskPayment)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        // 1. WATCH 6 ADS LOGIC
        btnWatchAd.setOnClickListener {
            showRewardedAd()
        }

        // 2. CREATE TASK - VALIDATION & SHOW PAYMENT
        btnPayForTask.setOnClickListener {
            if (etTitle.text.isBlank() || etDesc.text.isBlank() || etLink.text.isBlank()) {
                Toast.makeText(this, "Please fill all fields first!", Toast.LENGTH_SHORT).show()
            } else {
                cardTaskPayment.visibility = View.VISIBLE
                Toast.makeText(this, "Till 3043489 copied. Paste message below.", Toast.LENGTH_LONG).show()
            }
        }

        // 3. SUBMIT TASK TO ADMIN (Telegram)
        btnSubmitTask.setOnClickListener {
            val msg = etPaymentMsg.text.toString().trim()
            if (msg.length < 15) {
                Toast.makeText(this, "Please paste a valid M-Pesa/PayPal message", Toast.LENGTH_SHORT).show()
            } else {
                val taskData = """
                    🆕 NEW TASK REQUEST (1200 LIMIT)
                    Title: ${etTitle.text}
                    Desc: ${etDesc.text}
                    Link: ${etLink.text}
                    Payment Proof: $msg
                """.trimIndent()
                
                notifyAdminViaTelegram(taskData)
                Toast.makeText(this, "Task submitted! Admin will approve shortly.", Toast.LENGTH_LONG).show()
                finish() // Close activity
            }
        }

        // Support Click
        findViewById<TextView>(R.id.tvSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626"))
            startActivity(intent)
        }
    }

    private fun handleAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("last_ad_day", -1)
        var count = prefs.getInt("ad_count", 0)

        if (today != lastDay) {
            count = 0
            prefs.edit().putInt("last_ad_day", today).apply()
        }

        count++
        if (count >= 6) {
            val threeHours = 3 * 60 * 60 * 1000L
            prefs.edit().apply {
                putLong("expiry_time", System.currentTimeMillis() + threeHours)
                putInt("ad_count", 0)
                putBoolean("force_vpn", true) // Tells the VPN it CANNOT be stopped
                apply()
            }
            Toast.makeText(this, "3 Hours Force-Unlocked!", Toast.LENGTH_LONG).show()
            // Launch VPN directly
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
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", adminChatId)
            .add("text", message)
            .build()
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        // Replace with your real ID: ca-app-pub-2344867686796379/XXXXXXXXXX
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            })
    }

    private fun showRewardedAd() {
        rewardedAd?.let { ad ->
            ad.show(this) { 
                handleAdReward()
                loadRewardedAd() 
            }
        } ?: Toast.makeText(this, "Ad not ready...", Toast.LENGTH_SHORT).show()
    }
}
