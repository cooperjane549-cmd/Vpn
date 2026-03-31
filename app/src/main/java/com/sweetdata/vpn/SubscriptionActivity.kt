package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
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
        
        // CRITICAL: This MUST match the name of your file in res/layout/
        // If your file is activity_store.xml, change this to R.layout.activity_store
        try {
            setContentView(R.layout.activity_subscription)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout file not found!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE-ID"

        // UI Elements with Null-Safety
        val cardMpesa = findViewById<MaterialCardView>(R.id.cardMpesa)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyPayment)

        // 1. M-Pesa Copy Logic
        cardMpesa?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied! Pay 30 KES", Toast.LENGTH_SHORT).show()
        }

        // 2. Verification Logic
        btnVerify?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            
            if (msg.isEmpty()) {
                Toast.makeText(this, "Please paste the M-Pesa message", Toast.LENGTH_SHORT).show()
            } else if (msg.length < 15) {
                Toast.makeText(this, "Message too short. Paste full text!", Toast.LENGTH_SHORT).show()
            } else {
                val report = "💎 *SWEETDATA 24H REQUEST*\nID: $deviceId\n\n*Message:* $msg"
                sendToTelegram(report)
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

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    Toast.makeText(this@SubscriptionActivity, "Network Error: Failed to send", Toast.LENGTH_SHORT).show() 
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(this@SubscriptionActivity, "Sent! Admin will activate 24H soon.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@SubscriptionActivity, "Server Error: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
}
