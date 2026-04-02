package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SubscriptionActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val etMpesaMsg = findViewById<EditText>(R.id.etMpesaMessage)
        val btnPayMpesa = findViewById<MaterialButton>(R.id.btnPayMpesa)
        val btnPayPaypal = findViewById<MaterialButton>(R.id.btnPayPaypal)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitPayment)

        // M-PESA Click: Show Till & Copy to Clipboard
        btnPayMpesa.setOnClickListener {
            val till = "3043489"
            Toast.makeText(this, "Pay Kes 30 to Buy Goods Till: $till", Toast.LENGTH_LONG).show()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Till", till))
        }

        // PayPal Click: Open Link
        btnPayPaypal.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/L4GMUVK3ECXXA"))
            startActivity(intent)
        }

        // SUBMIT LOGIC
        btnSubmit.setOnClickListener {
            val proofText = etMpesaMsg.text.toString().trim()
            val user = auth.currentUser

            if (user == null) {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (proofText.isEmpty()) {
                Toast.makeText(this, "Please paste your payment message!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = HashMap<String, Any>()
            data["email"] = user.email ?: "No Email"
            data["uid"] = user.uid
            data["proof"] = proofText
            data["status"] = "pending"
            data["type"] = "SUB_24H"
            data["timestamp"] = System.currentTimeMillis()

            // Push to Firebase
            database.child("admin_verifications").push().setValue(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Sent! Admin will verify shortly.", Toast.LENGTH_LONG).show()
                    etMpesaMsg.setText("") // Clear the box after sending
                    finish() // Close activity
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
