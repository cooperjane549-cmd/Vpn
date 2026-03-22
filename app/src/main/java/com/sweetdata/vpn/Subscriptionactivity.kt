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

        // PayPal Link Integration
        findViewById<MaterialCardView>(R.id.cardPaypal).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/R886H7EXD7DZN")))
        }

        // M-Pesa Logic
        findViewById<MaterialCardView>(R.id.cardMpesa).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Till", "3043489"))
            Toast.makeText(this, "Till 3043489 copied! Pay and send screenshot.", Toast.LENGTH_LONG).show()
        }
    }
}

