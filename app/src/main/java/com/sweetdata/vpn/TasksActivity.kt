package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import java.io.IOException

class TasksActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PAYPAL_TASK_URL = "https://www.paypal.com/ncp/payment/E9WS362E37NPL" // $5 Link
    private val PREFS_NAME = "SweetDataPrefs"

    private var mInterstitialAd: InterstitialAd? = null
    private val client = OkHttpClient()
    private var tvAdProgress: TextView? = null
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE"

        tvAdProgress = findViewById(R.id.tvAdProgress)
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayPayPalTask = findViewById<MaterialButton>(R.id.btnPayPayPalTask)
        val btnCopyTill = findViewById<MaterialButton>(R.id.btnCopyTillTask)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        loadNextAd() // Pre-cache ad on entry
        updateAdProgressUI()

        // 1. WATCH AD FOR 2 HOURS (6 ADS)
        btnWatchAd?.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.show(this)
                handleAdReward()
            } else {
                Toast.makeText(this, "Ad still loading... please wait", Toast.LENGTH_SHORT).show()
                loadNextAd()
            }
        }

        // 2. CREATE TASK: PAYPAL ($5 / 1200 SPOTS)
        btnPayPayPalTask?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_TASK_URL)))
            Toast.makeText(this, "Pay $5, then paste proof below", Toast.LENGTH_LONG).show()
        }

        // 3. CREATE TASK: MPESA (KES 450 / 1200 SPOTS)
        btnCopyTill?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Till", "3043489"))
            Toast.makeText(this, "Till 3043489 Copied! Pay Kes 450", Toast.LENGTH_SHORT).show()
        }

        // 4. SUBMIT PROMO FOR ADMIN APPROVAL
        btnSubmitTask?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: ""
            val link = etLink?.text?.toString()?.trim() ?: ""
            val msg = etPaymentMsg?.text?.toString()?.trim() ?: ""

            if (title.isEmpty() || link.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "Fill all fields first!", Toast.LENGTH_SHORT).show()
            } else {
                val report = "🚀 *NEW TASK PROMO REQUEST*\n" +
                        "👤 ID: `$deviceId` \n" +
                        "📌 Title: $title\n" +
                        "🔗 Link: $link\n" +
                        "💰 Proof: $msg\n" +
                        "📊 Target: 1,200 Spots"
                sendToTelegram(report)
            }
        }
    }

    private fun handleAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt("ad_count", 0) + 1

        if (count >= 6) {
            val rewardMillis = 120 * 60 * 1000L // 2 Hours
            val currentExpiry = prefs.getLong("expiry_time", System.currentTimeMillis())
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            val newExpiryTime = baseTime + rewardMillis

            prefs.edit().putLong("expiry_time", newExpiryTime).putInt("ad_count", 0).apply()

            auth.currentUser?.uid?.let {
                database.child("users").child(it).child("expiry_time").setValue(newExpiryTime)
            }
            Toast.makeText(this, "REWARDED: 2 Hours Added!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            prefs.edit().putInt("ad_count", count).apply()
            updateAdProgressUI()
            loadNextAd() // Load next for the next click
        }
    }

    private fun updateAdProgressUI() {
        val count = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("ad_count", 0)
        tvAdProgress?.text = "AD PROGRESS: $count/6"
    }

    private fun loadNextAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { mInterstitialAd = null }
        })
    }

    private fun sendToTelegram(text: String) {
        val body = FormBody.Builder()
            .add("chat_id", ADMIN_CHAT_ID)
            .add("text", text)
            .add("parse_mode", "Markdown")
            .build()

        client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage").post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@TasksActivity, "Failed! Check connection", Toast.LENGTH_SHORT).show() }
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
