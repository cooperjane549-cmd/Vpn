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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SubscriptionActivity : AppCompatActivity() {

    private val PAYPAL_SUB_URL = "https://www.paypal.com/ncp/payment/L4GMUVK3ECXXA"
    private val auth = FirebaseAuth.getInstance()
    
    // I have set this to your specific project's database URL
    private val database: DatabaseReference = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference

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

        // PAYPAL
        btnPayPayPal?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_SUB_URL)))
        }

        // MPESA TILL COPY
        cardMpesa?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Till", "3043489")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Till 3043489 Copied!", Toast.LENGTH_SHORT).show()
        }

        // VERIFY / SUBMIT TO FIREBASE
        btnVerify?.setOnClickListener {
            val msg = etMpesaMessage?.text?.toString()?.trim() ?: ""
            
            if (msg.isEmpty()) {
                Toast.makeText(this, "Paste M-Pesa message first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Data to be saved
            val paymentData = HashMap<String, Any>()
            paymentData["email"] = userEmail
            paymentData["uid"] = userId
            paymentData["deviceId"] = deviceId
            paymentData["message"] = msg
            paymentData["timestamp"] = System.currentTimeMillis()
            paymentData["status"] = "pending"

            // Attempting to write to 'pending_payments' folder
            database.child("pending_payments").push().setValue(paymentData)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Submitted! Admin will activate soon.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    // This will show "Permission Denied" if Rules aren't set
                    // Or "Network Error" if the URL is wrong
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        // WHATSAPP
        btnContactAdmin?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254789574046")))
        }
    }
}
