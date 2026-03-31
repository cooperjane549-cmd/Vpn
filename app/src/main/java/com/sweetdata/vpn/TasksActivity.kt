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
import java.util.*

class TasksActivity : AppCompatActivity() {

    // --- CONFIGURATION ---
    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PREFS_NAME = "SweetDataPrefs"

    // Variables
    private var mInterstitialAd: InterstitialAd? = null
    private val client = OkHttpClient()
    private lateinit var tvAdProgress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // 1. Initialize Ads & UI
        tvAdProgress = findViewById(R.id.tvAdProgress)
        loadNextAd() // Pull first ad immediately
        updateAdProgressUI()

        // 2. Link Buttons & Views
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayForTask = findViewById<MaterialButton>(R.id.btnPayForTask)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        val cardTaskPayment = findViewById<MaterialCardView>(R.id.cardTaskPayment)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        // 3. WATCH AD LOGIC
        btnWatchAd.setOnClickListener {
            showAdAndReward()
        }

        // 4. PAY FOR TASK LOGIC (Reveals Payment & Copies Till)
        btnPayForTask.setOnClickListener {
            if (etTitle.text.isBlank() || etLink.text.isBlank()) {
                Toast.makeText(this, "Please enter Title and Link first!", Toast.LENGTH_SHORT).show()
            } else {
                // Show the hidden M-Pesa section
                cardTaskPayment.visibility = View.VISIBLE
                
                // AUTO-COPY TILL NUMBER
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Till Number", "3043489")
                clipboard.setPrimaryClip(clip)
                
                Toast.makeText(this, "Till 3043489 copied to clipboard!", Toast.LENGTH_LONG).show()
            }
        }

        // 5. SUBMIT TO TELEGRAM BOT
        btnSubmitTask.setOnClickListener {
            val msg = etPaymentMsg.text.toString().trim()
            if (msg.length < 10) {
                Toast.makeText(this, "Please paste the full M-Pesa message", Toast.LENGTH_SHORT).show()
            } else {
                val adminReport = """
                    🆕 *NEW TASK PROMOTION*
                    Title: ${etTitle.text}
                    Desc: ${etDesc.text}
                    Link: ${etLink.text}
                    ------------------------
                    💰 *PAYMENT PROOF:*
                    $msg
                """.trimIndent()
                
                sendToTelegram(adminReport)
            }
        }

        // 6. Support Link
        findViewById<TextView>(R.id.tvSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626"))
            startActivity(intent)
        }
    }

    // --- AD LOGIC (PULLER SYSTEM) ---

    private fun loadNextAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
        })
    }

    private fun showAdAndReward() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            mInterstitialAd = null // Clear used ad
            handleRewardPoints()
            loadNextAd() // Immediately pull the NEXT one
        } else {
            Toast.makeText(this, "Ad not ready, pulling new one...", Toast.LENGTH_SHORT).show()
            loadNextAd()
        }
    }

    private fun handleRewardPoints() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt("ad_count", 0)
        count++

        if (count >= 6) {
            val threeHours = 3 * 60 * 60 * 1000L
            prefs.edit().apply {
                putLong("expiry_time", System.currentTimeMillis() + threeHours)
                putInt("ad_count", 0)
                putBoolean("force_vpn", true)
                apply()
            }
            Toast.makeText(this, "3 Hours Unlimited Unlocked!", Toast.LENGTH_LONG).show()
            
            // Auto-Start VPN for the user
            startService(Intent(this, MyVpnService::class.java))
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

    // --- TELEGRAM SENDER ---

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
                runOnUiThread { Toast.makeText(this@TasksActivity, "Submission Failed!", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@TasksActivity, "Sent! Admin will approve soon.", Toast.LENGTH_LONG).show()
                    finish()
                }
                response.close()
            }
        })
    }
}
