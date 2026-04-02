package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class TasksActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        MobileAds.initialize(this)
        loadAd()

        // WORKER: Watch Ad
        findViewById<Button>(R.id.btnWatchAd).setOnClickListener {
            if (mInterstitialAd != null) mInterstitialAd?.show(this) 
            else { loadAd(); Toast.makeText(this, "Ad loading...", Toast.LENGTH_SHORT).show() }
        }

        // ADVERTISER: Payment Buttons
        findViewById<Button>(R.id.btnPayMpesa).setOnClickListener {
            Toast.makeText(this, "Pay 450 to Till 3043489", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnPayPaypal).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/E9WS362E37NPL")))
        }

        // ADVERTISER: Post Job Logic
        findViewById<Button>(R.id.btnSubmitAdRequest).setOnClickListener {
            val title = findViewById<EditText>(R.id.etAdTitle).text.toString().trim()
            val link = findViewById<EditText>(R.id.etAdLink).text.toString().trim()
            val proof = findViewById<EditText>(R.id.etPaymentProof).text.toString().trim()

            if (title.isEmpty() || proof.isEmpty()) {
                Toast.makeText(this, "Fill in Job Title and Proof Message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val jobData = mapOf(
                "uid" to (auth.currentUser?.uid ?: ""),
                "email" to (auth.currentUser?.email ?: ""),
                "jobTitle" to title,
                "jobLink" to link,
                "paymentProof" to proof,
                "status" to "pending",
                "type" to "SPROUT_JOB_POST",
                "timestamp" to System.currentTimeMillis()
            )

            database.child("admin_verifications").push().setValue(jobData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Job sent for verification!", Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun loadAd() {
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(p0: LoadAdError) { mInterstitialAd = null }
            })
    }
}
