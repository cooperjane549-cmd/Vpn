package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class SubscriptionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // PayPal Payment Link
        findViewById<MaterialCardView>(R.id.cardPaypal).setOnClickListener {
            val url = "https://www.paypal.com/ncp/payment/R886H7EXD7DZN"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // M-Pesa Manual Payment
        findViewById<MaterialCardView>(R.id.cardMpesa).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Till Number", "3043489")
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "Till 3043489 copied! Pay and send screenshot to Admin.", Toast.LENGTH_LONG).show()
        }
    }
}
