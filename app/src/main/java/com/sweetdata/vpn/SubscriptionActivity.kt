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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SubscriptionActivity : AppCompatActivity() {

    private val PAYPAL_SUB_URL = "https://www.paypal.com/ncp/payment/L4GMUVK3ECXXA"
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_UNKNOWN"
        val userEmail = auth.currentUser?.email ?: "No Email"
        val userId = auth.currentUser?.uid ?: "Unknown_UID"

        val btnPayPayPal = findViewById<MaterialButton>(R.id.btnPayPayPal)
        val cardMpesa = findViewById<MaterialCardView>(R.id.cardMpesa)
        val etMpesaMessage = findViewById<EditText>(R.id.etMpesaMessage)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyPayment)
        val btnContactAdmin = findViewById<TextView>(R.id.btnContactAdmin)

        // 1. PayPal Payment
        btnPayPayPal?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_SUB_URL)))
            Toast.makeText(this, "After paying, click VERIFY below", Toast.LENGTH_LONG).show()
        }

        // 2. M-Pesa Till Copy
        cardMpesa?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Till", "3043489"))
            Toast.makeText(this, "Till 3043489 Copied! Pay 30 KES", Toast.LENGTH_SHORT).show()
        }

        // 3. SUBMIT TO FIREBASE (Replaces Telegram)
        btnVerify?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            
            if (msg.isEmpty()) {
                Toast.makeText(this, "Please paste the M-Pesa message first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create a payment object
            val paymentData = HashMap<String, Any>()
            paymentData["email"] = userEmail
            paymentData["uid"] = userId
            paymentData["deviceId"] = deviceId
            paymentData["message"] = msg
            paymentData["timestamp"] = System.currentTimeMillis()
            paymentData["status"] = "pending"

            // Push to Firebase under 'pending_payments'
            database.child("pending_payments").push().setValue(paymentData)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Submitted! Admin will activate you soon.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Upload Failed! Use WhatsApp instead.", Toast.LENGTH_LONG).show()
                }
        }

        // 4. WhatsApp Support
        btnContactAdmin?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254789574046")))
        }
    }
}
