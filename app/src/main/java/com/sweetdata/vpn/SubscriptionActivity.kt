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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import okhttp3.FormBody
import java.io.IOException

class SubscriptionActivity : AppCompatActivity() {

    // Your Admin Credentials
    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    private val PAYPAL_SUB_URL = "https://www.paypal.com/ncp/payment/L4GMUVK3ECXXA" // $0.25 Link
    
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_UNKNOWN"

        // UI Components
        val btnPayPayPal = findViewById<MaterialButton>(R.id.btnPayPayPal)
        val cardMpesa = findViewById<MaterialCardView>(R.id.cardMpesa)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyPayment)
        val btnContactAdmin = findViewById<TextView>(R.id.btnContactAdmin)

        // 1. GLOBAL PAYMENT: PayPal ($0.25)
        btnPayPayPal?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_SUB_URL))
            startActivity(intent)
            Toast.makeText(this, "After paying, click VERIFY below", Toast.LENGTH_LONG).show()
        }

        // 2. LOCAL PAYMENT: M-Pesa Till Copy
        cardMpesa?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied! Pay 30 KES", Toast.LENGTH_SHORT).show()
        }

        // 3. SUBMISSION BRIDGE (Telegram)
        btnVerify?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            
            if (msg.isEmpty()) {
                // If they paid via PayPal, they might just want to send their ID
                val report = "💎 *PAYPAL/SUBSCRIPTION VERIFY*\nID: `$deviceId`"
                sendToTelegram(report)
            } else {
                // Standard M-Pesa flow
                val report = "💎 *MPESA SUBSCRIPTION*\nID: `$deviceId`\n\n*Message:* $msg"
                sendToTelegram(report)
            }
        }

        // 4. WHATSAPP SUPPORT (Manual Payments/Help)
        btnContactAdmin?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626"))
            startActivity(intent)
        }
    }

    private fun sendToTelegram(text: String) {
        val url = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
        
        // Using FormBody prevents 404/Encoding errors with M-Pesa symbols
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
                    Toast.makeText(this@SubscriptionActivity, "Network Error. Check internet.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SubscriptionActivity, "Request Sent! Admin will activate soon.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this@SubscriptionActivity, "Error: ${response.code}. Try WhatsApp.", Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }
}
