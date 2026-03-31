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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import java.io.IOException

class SubscriptionActivity : AppCompatActivity() {

    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_subscription)
        } catch (e: Exception) {
            // This catches theme or XML errors so the app doesn't just "vanish"
            Toast.makeText(this, "Layout Error: Check your XML theme", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE"

        // Link UI Elements from your XML
        val cardMpesa = findViewById<MaterialCardView>(R.id.cardMpesa)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyPayment)
        val btnContactAdmin = findViewById<TextView>(R.id.btnContactAdmin)

        // 1. Copy Till Number Logic
        cardMpesa?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied! Pay 30 KES", Toast.LENGTH_SHORT).show()
        }

        // 2. Submit Logic
        btnVerify?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            if (msg.isEmpty()) {
                Toast.makeText(this, "Please paste the M-Pesa message first", Toast.LENGTH_SHORT).show()
            } else if (msg.length < 15) {
                Toast.makeText(this, "Message too short! Paste the full text", Toast.LENGTH_SHORT).show()
            } else {
                val report = "💎 *SUBSCRIPTION REQUEST*\nID: $deviceId\n\n*Message:* $msg"
                sendToTelegram(report)
            }
        }

        // 3. WhatsApp Support Logic
        btnContactAdmin?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }
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
                runOnUiThread { 
                    Toast.makeText(this@SubscriptionActivity, "Network Error. Try again.", Toast.LENGTH_SHORT).show() 
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SubscriptionActivity, "Sent! 24H activating soon.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this@SubscriptionActivity, "Server Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }
}
