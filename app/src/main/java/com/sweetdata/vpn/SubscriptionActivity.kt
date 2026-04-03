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
            // Fresh check of the Firebase Auth session
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            val proofText = etMessage.text.toString().trim()

            // Block submission if user is missing OR is a 'Guest/Anonymous' user
            if (user == null || user.isAnonymous) {
                Toast.makeText(this, "Login error: Please sign in with Google to SweetData VPN", Toast.LENGTH_LONG).show()
                
                // Optional: Force return to Main if they aren't logged in
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            } else if (proofText.isEmpty()) {
                Toast.makeText(this, "Please paste your payment confirmation message", Toast.LENGTH_SHORT).show()
            } else {
                // If everything is perfect, submit to Firebase
                submitToAdmin(user.uid, user.email ?: "No Email", proofText)
            }
        }
    }

    private fun submitToAdmin(uid: String, email: String, proof: String) {
        val database = FirebaseDatabase.getInstance().reference
        
        // Data packet for your review in the Firebase Console
        val verificationData = mapOf(
            "uid" to uid,
            "email" to email,
            "proofText" to proof,
            "status" to "pending",
            "type" to "PREMIUM_24HR_REQUEST",
            "timestamp" to System.currentTimeMillis()
        )

        // Push to 'admin_verifications' node
        database.child("admin_verifications").push().setValue(verificationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Proof submitted! Admin is verifying your payment.", Toast.LENGTH_LONG).show()
                finish() // Takes user back to the dashboard
            }
            .addOnFailureListener {
                Toast.makeText(this, "Submission failed. Please check your data connection.", Toast.LENGTH_SHORT).show()
            }
    }
}
