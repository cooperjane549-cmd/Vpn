package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import java.io.IOException

class TasksActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PREFS_NAME = "SweetDataPrefs"

    private var mInterstitialAd: InterstitialAd? = null
    private val client = OkHttpClient()
    private lateinit var tvAdProgress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CHECK THIS: Ensure your XML file is named activity_task.xml
        setContentView(R.layout.activity_task)
    

        tvAdProgress = findViewById(R.id.tvAdProgress)
        loadNextAd()
        updateAdProgressUI()

        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayForTask = findViewById<MaterialButton>(R.id.btnPayForTask)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        val cardTaskPayment = findViewById<MaterialCardView>(R.id.cardTaskPayment)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        btnWatchAd.setOnClickListener {
            showAdAndReward()
        }

        btnPayForTask.setOnClickListener {
            if (etTitle.text.isBlank() || etLink.text.isBlank()) {
                Toast.makeText(this, "Enter Title and Link first!", Toast.LENGTH_SHORT).show()
            } else {
                cardTaskPayment.visibility = View.VISIBLE
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Till Number", "3043489")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Till 3043489 copied!", Toast.LENGTH_LONG).show()
            }
        }

        btnSubmitTask.setOnClickListener {
            val msg = etPaymentMsg.text.toString().trim()
            if (msg.length < 10) {
                Toast.makeText(this, "Paste full M-Pesa message", Toast.LENGTH_SHORT).show()
            } else {
                val adminReport = """
                    🆕 *NEW TASK PROMOTION*
                    Title: ${etTitle.text}
                    Link: ${etLink.text}
                    💰 *PROOF:* $msg
                """.trimIndent()
                sendToTelegram(adminReport)
            }
        }

        findViewById<TextView>(R.id.tvSupport).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626")))
        }
    }

    private fun loadNextAd() {
        InterstitialAd.load(this, INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
        })
    }

    private fun showAdAndReward() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            mInterstitialAd = null
            handleRewardPoints()
            loadNextAd()
        } else {
            Toast.makeText(this, "Ad loading... try again in 3 seconds", Toast.LENGTH_SHORT).show()
            loadNextAd()
        }
    }

    private fun handleRewardPoints() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt("ad_count", 0)
        count++

        if (count >= 6) {
            // 2 Hours = 120 Minutes
            val twoHours = 120 * 60 * 1000L
            val currentExpiry = prefs.getLong("expiry_time", System.currentTimeMillis())
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            
            prefs.edit().apply {
                putLong("expiry_time", baseTime + twoHours)
                putInt("ad_count", 0)
                apply()
            }
            Toast.makeText(this, "2 Hours Added!", Toast.LENGTH_LONG).show()
            finish() 
        } else {
            prefs.edit().putInt("ad_count", count).apply()
            updateAdProgressUI()
        }
    }

    private fun updateAdProgressUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("ad_count", 0)
        tvAdProgress.text = "Progress: $count/6 Ads Watched"
    }

    private fun sendToTelegram(text: String) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", ADMIN_CHAT_ID)
            .add("text", text)
            .add("parse_mode", "Markdown")
            .build()

        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@TasksActivity, "Failed!", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@TasksActivity, "Sent! Waiting for approval.", Toast.LENGTH_LONG).show()
                    finish()
                }
                response.close()
            }
        })
    }
}
