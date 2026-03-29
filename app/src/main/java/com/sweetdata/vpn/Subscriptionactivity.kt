package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import java.io.IOException
import java.util.UUID

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var userReferralCode: String
    private val client = OkHttpClient()

    // Telegram Credentials
    private val botToken = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val adminChatId = "6847108451"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // Generate Referral Code
        userReferralCode = UUID.randomUUID().toString().substring(0, 8).uppercase()
        findViewById<TextView>(R.id.tvReferralCode).text = "Your Code: $userReferralCode"

        // New: Find the Paste M-Pesa EditText (Ensure this ID is in your XML)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerifyPayment = findViewById<MaterialButton>(R.id.btnVerifyPayment)

        // 1. PayPal Payment Click
        findViewById<MaterialCardView>(R.id.cardPaypal).setOnClickListener {
            val paypalUrl = "https://www.paypal.com/ncp/payment/ADJDGV25FGBP4"
            notifyTelegram("💳 PAYPAL INITIATED\nUser ID: $userReferralCode")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl)))
        }

        // 2. M-Pesa Click (Copy Till + Open App)
        findViewById<MaterialCardView>(R.id.cardMpesa).setOnClickListener {
            val tillNumber = "3043489"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till Number", tillNumber)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "Till $tillNumber copied. Opening M-Pesa...", Toast.LENGTH_LONG).show()

            try {
                val intent = packageManager.getLaunchIntentForPackage("com.safaricom.mpesa.orgapp")
                if (intent != null) startActivity(intent) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tel:*334#")))
            } catch (e: Exception) {
                // Fallback if no app found
            }
        }

        // 3. NEW: Verify M-Pesa Message Logic
        btnVerifyPayment.setOnClickListener {
            val message = etMpesaMessage.text.toString().trim()
            if (message.length > 20) {
                notifyTelegram("✅ PAYMENT SUBMITTED\nUser ID: $userReferralCode\nMessage: $message")
                Toast.makeText(this, "Payment details sent to Admin for approval!", Toast.LENGTH_LONG).show()
                etMpesaMessage.setText("") // Clear after sending
            } else {
                Toast.makeText(this, "Please paste the full M-Pesa message", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Share Referral to WhatsApp
        findViewById<MaterialButton>(R.id.btnShareReferral).setOnClickListener {
            val shareMsg = "Get fast internet with SweetData VPN! Use my code: $userReferralCode. Download here: https://sweetdata.cam"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMsg)
            }
            try {
                intent.`package` = "com.whatsapp"
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent.createChooser(intent, "Share via"))
            }
        }

        // 5. Contact Admin (WhatsApp)
        findViewById<MaterialButton>(R.id.btnContactAdmin).setOnClickListener {
            val phone = "+254789574046" 
            val msg = "Hello Admin, I paid for VIP. ID: $userReferralCode"
            val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // --- TELEGRAM SENDING LOGIC ---
    private fun notifyTelegram(message: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        
        val formBody = FormBody.Builder()
            .add("chat_id", adminChatId)
            .add("text", message)
            .build()

        val request = Request.Builder().url(url).post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Telegram", "Failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
