package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class TasksActivity : AppCompatActivity() {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // Load Initial Ad
        loadNextAd()

        // --- SECTION 1: ADS (2 HOURS) ---
        val btnWatchAd = findViewById<MaterialButton>(R.id.btnWatchAd)
        btnWatchAd.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        Toast.makeText(this@TasksActivity, "Ad watched! Progress saved.", Toast.LENGTH_SHORT).show()
                        loadNextAd()
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                Toast.makeText(this, "Ad is still loading...", Toast.LENGTH_SHORT).show()
                loadNextAd()
            }
        }

        // --- SECTION 2: WORKER PROOF (5 MINS) ---
        val etWorkProof = findViewById<EditText>(R.id.etWorkProof)
        val btnSubmitWorkProof = findViewById<MaterialButton>(R.id.btnSubmitWorkProof)
        
        btnSubmitWorkProof.setOnClickListener {
            val proofText = etWorkProof.text.toString().trim()
            if (proofText.isNotEmpty()) {
                submitRequest(proofText, "WORK_5MIN")
            } else {
                Toast.makeText(this, "Please enter proof of work", Toast.LENGTH_SHORT).show()
            }
        }

        // --- SECTION 3: ADVERTISER PAYMENT & TASK CREATION ($5) ---
        val btnPayAdvertiserMpesa = findViewById<MaterialButton>(R.id.btnPayAdvertiserMpesa)
        val btnPayAdvertiserPaypal = findViewById<MaterialButton>(R.id.btnPayAdvertiserPaypal)
        
        // This ID now matches the "Dark Card" XML exactly
        val etAdvertiserTaskDetails = findViewById<EditText>(R.id.etAdvertiserTaskDetails)
        val btnSubmitAdvertiserTask = findViewById<MaterialButton>(R.id.btnSubmitAdvertiserTask)

        btnPayAdvertiserMpesa.setOnClickListener {
            Toast.makeText(this, "Pay Kes 450 to Till: 3043489", Toast.LENGTH_LONG).show()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Till", "3043489"))
        }

        btnPayAdvertiserPaypal.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/E9WS362E37NPL"))
            startActivity(intent)
        }

        btnSubmitAdvertiserTask.setOnClickListener {
            val taskInfo = etAdvertiserTaskDetails.text.toString().trim()
            if (taskInfo.isNotEmpty()) {
                submitRequest(taskInfo, "AD_LISTING_450")
            } else {
                Toast.makeText(this, "Enter Task Details & Payment Proof", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitRequest(proof: String, type: String) {
        val user = auth.currentUser ?: return
        val data = HashMap<String, Any>()
        data["email"] = user.email ?: "No Email"
        data["uid"] = user.uid
        data["proof"] = proof
        data["status"] = "pending"
        data["type"] = type
        data["timestamp"] = System.currentTimeMillis()

        database.child("admin_verifications").push().setValue(data)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Submitted! Admin will verify soon.", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadNextAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                mInterstitialAd = ad
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }
        })
    }
}
