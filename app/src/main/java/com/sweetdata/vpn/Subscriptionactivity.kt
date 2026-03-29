package com.sweetdata.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    // Your Telegram Credentials
    private val botToken = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val adminChatId = "6847108451"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // Generate or Retrieve Referral Code (Persistent for the session)
        userReferralCode = UUID.randomUUID().toString().substring(0, 8).uppercase()
        findViewById<TextView>(R.id.tvReferralCode).text = "Your ID: $userReferralCode"

        // 1. PayPal (Direct Link)
        findViewById<MaterialCardView>(R.id.cardPaypal).setOnClickListener {
            notifyTelegram("User $userReferralCode clicked PayPal link")
            val paypalUrl = "https://www.paypal.com/ncp/payment/ADJDGV25FGBP4"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl)))
        }

        // 2. M-Pesa (Automated Copy + Telegram Alert + Open M-Pesa)
        findViewById<MaterialCardView>(R.id.cardMpesa).setOnClickListener {
            val tillNumber = "3043489"
            
            // Copy to clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Till", tillNumber))
            
            // Alert Admin via Telegram
            notifyTelegram("🚨 PAYMENT INITIATED\nUser ID: $userReferralCode\nMethod: M-Pesa\nTill: $tillNumber\nStatus: Waiting for screenshot")

            Toast.makeText(this, "Till $tillNumber copied! Opening M-Pesa...", Toast.LENGTH_LONG).show()

            // Try to open Safaricom/M-Pesa App
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.safaricom.mpesa.orgapp")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    // Fallback to Sim Toolkit dialer (Kenya specific)
                    val simToolKit = Intent(Intent.ACTION_VIEW, Uri.parse("tel:*334#"))
                    startActivity(simToolKit)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Please open M-Pesa and pay to Till $tillNumber", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Share Referral
        findViewById<MaterialButton>(R.id.btnShareReferral).setOnClickListener {
            val shareMsg = "Get fast internet with SweetData VPN! Use code: $userReferralCode. Download: https://sweetdata.cam"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMsg)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        // 4. Contact Admin (WhatsApp - The "Manual" Verification Step)
        findViewById<MaterialButton>(R.id.btnContactAdmin).setOnClickListener {
            val phone = "+254789574046"
            val msg = "Hello Admin, I have paid. My ID is $userReferralCode. Please activate my VIP."
            val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // --- TELEGRAM BOT LOGIC ---
    private fun notifyTelegram(message: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$adminChatId&text=${Uri.encode(message)}"
        
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramBot", "Failed to send: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
