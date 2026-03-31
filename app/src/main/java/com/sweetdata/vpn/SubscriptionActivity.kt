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
        
        // CRITICAL CHECK: Ensure your file in res/layout is activity_subscription.xml
        try {
            setContentView(R.layout.activity_subscription)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout Error: activity_subscription missing!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"

        // 1. Referral Section
        val tvReferralCode = findViewById<TextView>(R.id.tvReferralCode)
        val btnShareReferral = findViewById<MaterialButton>(R.id.btnShareReferral)
        
        val myReferralCode = "SD-" + deviceId.takeLast(6).uppercase()
        tvReferralCode?.text = "Your Code: $myReferralCode"

        btnShareReferral?.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Get SweetData VPN! Use my Referral Code: $myReferralCode to get extra time.")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Referral Code"))
        }

        // 2. PayPal Section
        findViewById<MaterialCardView>(R.id.cardPaypal)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/paypalme/youraccount/0.25"))
            startActivity(intent)
        }

        // 3. M-Pesa Section (Till Copy)
        findViewById<MaterialCardView>(R.id.cardMpesa)?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied!", Toast.LENGTH_SHORT).show()
        }

        // 4. Verification Logic
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerifyPayment = findViewById<MaterialButton>(R.id.btnVerifyPayment)

        btnVerifyPayment?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            if (msg.length < 15) {
                Toast.makeText(this, "Paste the full M-Pesa/PayPal message!", Toast.LENGTH_SHORT).show()
            } else {
                val report = """
                    💎 *SWEETDATA VIP REQUEST*
                    ----------------------------
                    *Device ID:* `$deviceId`
                    *Referral:* `$myReferralCode`
                    
                    *Payment Message:*
                    $msg
                """.trimIndent()
                sendToTelegram(report)
            }
        }

        // 5. Contact Admin
        findViewById<MaterialButton>(R.id.btnContactAdmin)?.setOnClickListener {
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
                runOnUiThread { Toast.makeText(this@SubscriptionActivity, "Network Error. Try again.", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@SubscriptionActivity, "Proof sent! VIP will be active soon.", Toast.LENGTH_LONG).show()
                    finish()
                }
                response.close()
            }
        })
    }
}
