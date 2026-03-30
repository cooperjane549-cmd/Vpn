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

    // --- CONFIGURATION ---
    private val BOT_TOKEN = "8704489723:AAESi-hHMCYK1mVNLIGP69maZX7lOu7eaMg"
    private val ADMIN_CHAT_ID = "6847108451"
    
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Correctly pointing to activity_subscription.xml
        setContentView(R.layout.activity_subscription)

        // 1. Get Device ID (Unique to this phone - Locked for SweetData VIP)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // 2. Referral Logic (SweetData VPN Branded)
        val tvReferralCode = findViewById<TextView>(R.id.tvReferralCode)
        val btnShareReferral = findViewById<MaterialButton>(R.id.btnShareReferral)
        
        val myReferralCode = "SD-" + deviceId.takeLast(6).uppercase()
        tvReferralCode.text = "Your Code: $myReferralCode"

        btnShareReferral.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Get unlimited high-speed internet with SweetData VPN! Use my code $myReferralCode. Download here: [Your Play Store Link]")
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        // 3. M-Pesa Card (Auto-Copy Till Number)
        val cardMpesa = findViewById<MaterialCardView>(R.id.cardMpesa)
        cardMpesa.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till Number", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 copied! Paste in M-Pesa", Toast.LENGTH_LONG).show()
        }

        // 4. PayPal Card
        val cardPaypal = findViewById<MaterialCardView>(R.id.cardPaypal)
        cardPaypal.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/paypalme/youraccount/5"))
            startActivity(browserIntent)
        }

        // 5. VIP Verification Logic (Telegram Bot Integration)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyPayment)

        btnVerify.setOnClickListener {
            val paymentMsg = etMpesaMessage.text.toString().trim()
            
            if (paymentMsg.length < 15) {
                Toast.makeText(this, "Please paste the full M-Pesa/PayPal message", Toast.LENGTH_SHORT).show()
            } else {
                val adminReport = """
                    💎 *SWEETDATA VIP REQUEST* 💎
                    
                    *Device ID:* `$deviceId`
                    *Referral Code:* $myReferralCode
                    
                    --------------------------
                    📝 *PAYMENT MESSAGE:*
                    $paymentMsg
                    
                    --------------------------
                    *ACTION:* 
                    Click to link this Device ID to VIP:
                    https://your-backend-api.com/approve?id=$deviceId
                """.trimIndent()
                
                sendToTelegram(adminReport)
            }
        }

        // 6. Contact Admin (WhatsApp Support)
        findViewById<MaterialButton>(R.id.btnContactAdmin).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254799978626"))
            startActivity(intent)
        }
    }

    // --- TELEGRAM NETWORK FUNCTION ---
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
                    Toast.makeText(this@SubscriptionActivity, "Submission Failed! Check Internet.", Toast.LENGTH_SHORT).show() 
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@SubscriptionActivity, "SweetData Admin Notified! Activating VIP soon.", Toast.LENGTH_LONG).show()
                    finish() 
                }
                response.close()
            }
        })
    }
}
