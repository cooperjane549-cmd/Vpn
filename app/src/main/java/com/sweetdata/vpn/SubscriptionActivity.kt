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

        val etMsg = findViewById<EditText>(R.id.etMpesaMessage)
        
        findViewById<MaterialButton>(R.id.btnPayMpesa).setOnClickListener {
            Toast.makeText(this, "Pay Kes 30 to Till: 3043489", Toast.LENGTH_LONG).show()
        }
        
        findViewById<MaterialButton>(R.id.btnPayPaypal).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/L4GMUVK3ECXXA")))
        }

        findViewById<MaterialButton>(R.id.btnSubmitPayment).setOnClickListener {
            val proof = etMsg.text.toString().trim()
            val user = auth.currentUser ?: return@setOnClickListener
            if (proof.isEmpty()) return@setOnClickListener

            val data = HashMap<String, Any>()
            data["email"] = user.email ?: ""
            data["uid"] = user.uid
            data["proof"] = proof
            data["status"] = "pending"
            data["type"] = "SUB_24H"

            database.child("admin_verifications").push().setValue(data)
                .addOnSuccessListener { Toast.makeText(this, "✅ Sent! Waiting for 24H Activation.", Toast.LENGTH_LONG).show(); finish() }
        }
    }
}
