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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import java.io.IOException

class TasksActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val INTERSTITIAL_ID = "ca-app-pub-2344867686796379/4612206920"
    private val PREFS_NAME = "SweetDataPrefs"

    private var mInterstitialAd: InterstitialAd? = null
    private val client = OkHttpClient()
    private var tvAdProgress: TextView? = null
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // 1. Link UI with safe-finds
        tvAdProgress = findViewById(R.id.tvAdProgress)
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        val btnPayForTask = findViewById<MaterialButton>(R.id.btnPayForTask)
        val btnSubmitTask = findViewById<MaterialButton>(R.id.btnSubmitTaskToAdmin)
        val cardTaskPayment = findViewById<MaterialCardView>(R.id.cardTaskPayment)
        
        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val etLink = findViewById<EditText>(R.id.etTaskLink)
        val etPaymentMsg = findViewById<EditText>(R.id.etTaskPaymentMsg)

        loadNextAd()
        updateAdProgressUI()

        // 2. Watch Ad Button
        btnWatchAd?.setOnClickListener {
            showAdAndReward()
        }

        // 3. Pay for Task Button
        btnPayForTask?.setOnClickListener {
            val titleText = etTitle?.text?.toString()?.trim() ?: ""
            if (titleText.isEmpty()) {
                Toast.makeText(this, "Enter Title first!", Toast.LENGTH_SHORT).show()
            } else {
                cardTaskPayment?.visibility = View.VISIBLE
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Till", "3043489"))
                Toast.makeText(this, "Till 3043489 Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Submit Proof Button
        btnSubmitTask?.setOnClickListener {
            val msg = etPaymentMsg?.text?.toString()?.trim() ?: ""
            if (msg.length < 10) {
                Toast.makeText(this, "Paste the full M-Pesa message", Toast.LENGTH_SHORT).show()
            } else {
                val report = "🆕 *TASK PROMO*\nTitle: ${etTitle?.text}\nLink: ${etLink?.text}\nProof: $msg"
                sendToTelegram(report)
            }
        }

        // 5. WhatsApp Support
        findViewById<TextView>(R.id.tvSupport)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626")))
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            mInterstitialAd = null
            handleRewardPoints()
            loadNextAd()
        } else {
            Toast.makeText(this, "Ad loading... try again", Toast.LENGTH_SHORT).show()
            loadNextAd()
        }
    }

    private fun handleRewardPoints() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = prefs.getInt("ad_count", 0) + 1

        if (count >= 6) {
            val rewardMillis = 120 * 60 * 1000L
            val currentExpiry = prefs.getLong("expiry_time", System.currentTimeMillis())
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            val newExpiryTime = baseTime + rewardMillis

            prefs.edit().putLong("expiry_time", newExpiryTime).putInt("ad_count", 0).apply()

            auth.currentUser?.uid?.let {
                database.child("users").child(it).child("expiry_time").setValue(newExpiryTime)
            }
            Toast.makeText(this, "2 Hours Added!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            prefs.edit().putInt("ad_count", count).apply()
            updateAdProgressUI()
        }
    }

    private fun updateAdProgressUI() {
        val count = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("ad_count", 0)
        tvAdProgress?.text = "Progress: $count/6 Ads Watched"
    }

    private fun sendToTelegram(text: String) {
        val body = FormBody.Builder()
            .add("chat_id", ADMIN_CHAT_ID)
            .add("text", text)
            .add("parse_mode", "Markdown")
            .build()

        client.newCall(Request.Builder().url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage").post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { 
                    Toast.makeText(this@TasksActivity, "Sent for approval!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        })
    }
}
