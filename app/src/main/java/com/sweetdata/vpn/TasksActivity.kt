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
import okhttp3.*
import java.io.IOException

class TasksActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PAYPAL_TASK_URL = "https://www.paypal.com/ncp/payment/E9WS362E37NPL"
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

        // Start loading the first ad immediately
        loadNextAd()
        updateAdProgressUI()

        // 1. WATCH AD LOGIC
        btnWatchAd?.setOnClickListener {
            if (mInterstitialAd != null) {
                // Set the callback to handle what happens AFTER the ad finishes
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        handleAdReward() // Give reward only after they watch
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        mInterstitialAd = null
                        loadNextAd()
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                Toast.makeText(this, "Ad still loading... Please wait 5 seconds", Toast.LENGTH_SHORT).show()
                loadNextAd() // Force a reload if it was null
            }
        }

        // 2. PAYPAL LINK
        btnPayPayPalTask?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_TASK_URL))
            startActivity(intent)
            Toast.makeText(this, "Pay $5, then paste proof below", Toast.LENGTH_LONG).show()
        }

        // 3. MPESA TILL COPY
        btnCopyTill?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied!", Toast.LENGTH_SHORT).show()
        }

        // 4. SUBMIT TASK TO ADMIN
        btnSubmitTask?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: ""
            val link = etLink?.text?.toString()?.trim() ?: ""
            val msg = etPaymentMsg?.text?.toString()?.trim() ?: ""
            val userEmail = auth.currentUser?.email ?: "Unknown"

            if (title.isEmpty() || link.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else {
                // Using HTML mode for the bot to avoid 'Markdown' symbol errors
                val report = "<b>🚀 NEW TASK PROMO REQUEST</b>\n\n" +
                        "<b>User:</b> $userEmail\n" +
                        "<b>ID:</b> <code>$deviceId</code>\n" +
                        "<b>Title:</b> $title\n" +
                        "<b>Link:</b> $link\n\n" +
                        "<b>Proof Message:</b>\n$msg"
                
                sendToTelegram(report)
            }
        }
    }

    private fun handleAdReward() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("ad_count", 0) + 1

        if (currentCount >= 6) {
            val rewardMillis = 120 * 60 * 1000L // 2 Hours
            val currentExpiry = prefs.getLong("expiry_time", System.currentTimeMillis())
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            val newExpiryTime = baseTime + rewardMillis

            // Save locally
            prefs.edit().putLong("expiry_time", newExpiryTime).putInt("ad_count", 0).apply()

            // Save to Firebase
            auth.currentUser?.uid?.let { uid ->
                database.child("users").child(uid).child("expiry_time").setValue(newExpiryTime)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ 2 Hours Added Successfully!", Toast.LENGTH_LONG).show()
                        finish() // Go back to MainActivity to see updated time
                    }
            }
        } else {
            prefs.edit().putInt("ad_count", currentCount).apply()
            updateAdProgressUI()
            loadNextAd() // Load the next ad for the next click
            Toast.makeText(this, "Ad $currentCount/6 complete!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAdProgressUI() {
        val count = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("ad_count", 0)
        runOnUiThread {
            if (mInterstitialAd != null) {
                tvAdProgress?.text = "AD READY: $count/6"
            } else {
                tvAdProgress?.text = "LOADING AD: $count/6..."
            }
        }
    }

    private fun loadNextAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                mInterstitialAd = ad
                updateAdProgressUI()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                mInterstitialAd = null
                updateAdProgressUI()
                // Auto-retry after 10 seconds if it fails
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadNextAd()
                }, 10000)
            }
        })
    }

    private fun sendToTelegram(htmlText: String) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
        
        val body = FormBody.Builder()
            .add("chat_id", ADMIN_CHAT_ID)
            .add("text", htmlText)
            .add("parse_mode", "HTML")
            .build()

        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@TasksActivity, "Failed to send to bot!", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@TasksActivity, "Sent! Admin will approve soon.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        // Shows the error code (e.g., 404 or 400) to help us debug
                        Toast.makeText(this@TasksActivity, "Server Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }
}
