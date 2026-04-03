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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val btnMpesa = findViewById<MaterialButton>(R.id.btnMpesaInstructions)
        val btnPaypal = findViewById<MaterialButton>(R.id.btnPaypalLink)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitPayment)
        val etMessage = findViewById<EditText>(R.id.etMpesaMessage)

        btnMpesa.setOnClickListener {
            Toast.makeText(this, "Pay Kes 30 to Buy Goods Till: 3043489", Toast.LENGTH_LONG).show()
        }

        btnPaypal.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/E9WS362E37NPL"))
            startActivity(intent)
        }

        btnSubmit.setOnClickListener {
            // Check auth status exactly when clicking
            val user = FirebaseAuth.getInstance().currentUser
            val proofText = etMessage.text.toString().trim()

            if (user == null) {
                Toast.makeText(this, "Error: Please sign in to SweetData VPN first", Toast.LENGTH_LONG).show()
            } else if (proofText.isEmpty()) {
                Toast.makeText(this, "Please paste your payment confirmation message", Toast.LENGTH_SHORT).show()
            } else {
                submitToAdmin(user.uid, user.email ?: "No Email", proofText)
            }
        }
    }

    private fun submitToAdmin(uid: String, email: String, proof: String) {
        val database = FirebaseDatabase.getInstance().reference
        val verificationData = mapOf(
            "uid" to uid,
            "email" to email,
            "proofText" to proof,
            "status" to "pending",
            "type" to "PREMIUM_24HR_REQUEST",
            "timestamp" to System.currentTimeMillis()
        )

        database.child("admin_verifications").push().setValue(verificationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment submitted! Admin will activate your 24hrs soon.", Toast.LENGTH_LONG).show()
                finish() // Returns to MainActivity
            }
            .addOnFailureListener {
                Toast.makeText(this, "Submission failed. Check your internet connection.", Toast.LENGTH_SHORT).show()
            }
    }
}
