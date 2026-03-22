package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.UUID

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var userReferralCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // Generate Referral Code
        userReferralCode = UUID.randomUUID().toString().substring(0, 8).uppercase()
        findViewById<TextView>(R.id.tvReferralCode).text = "Your Code: $userReferralCode"

        // 1. PayPal Payment Click
        findViewById<MaterialCardView>(R.id.cardPaypal).setOnClickListener {
            val paypalUrl = "https://www.paypal.com/ncp/payment/ADJDGV25FGBP4
            "
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
            startActivity(intent)
        }

        // 2. M-Pesa Click (Copy Till Number)
        findViewById<MaterialCardView>(R.id.cardMpesa).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Till Number", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 copied. Pay and send screenshot.", Toast.LENGTH_LONG).show()
        }

        // 3. Share Referral to WhatsApp
        findViewById<MaterialButton>(R.id.btnShareReferral).setOnClickListener {
            val shareMsg = "Get fast internet with SweetData VPN! Use my code: $userReferralCode to get 50MB free. Download here: https://sweetdata.cam"
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            intent.setPackage("com.whatsapp")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent.createChooser(intent, "Share via"))
            }
        }

        // 4. Contact Admin (WhatsApp)
        findViewById<MaterialButton>(R.id.btnContactAdmin).setOnClickListener {
            val phone = "+2547XXXXXXXX" // REPLACE with your actual WhatsApp number
            val msg = "Hello Admin, I paid for VIP on SweetData. Here is my screenshot. My code is $userReferralCode"
            val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
