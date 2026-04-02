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

        loadNextAd()

        // 1. WATCH ADS FOR 2 HOURS (AUTO)
        findViewById<MaterialButton>(R.id.btnWatchAd).setOnClickListener {
            mInterstitialAd?.show(this) ?: loadNextAd()
        }

        // 2. SUBMIT WORK PROOF (FACEBOOK/ETC) FOR 5 MINS
        findViewById<MaterialButton>(R.id.btnSubmitWorkProof).setOnClickListener {
            submitRequest(findViewById<EditText>(R.id.etWorkProof).text.toString(), "WORK_5MIN")
        }

        // 3. ADVERTISER PAYS KES 450 TO LIST TASK
        findViewById<MaterialButton>(R.id.btnPayAdvertiserMpesa).setOnClickListener {
            Toast.makeText(this, "Pay Kes 450 to Till: 3043489", Toast.LENGTH_LONG).show()
        }
        findViewById<MaterialButton>(R.id.btnPayAdvertiserPaypal).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/E9WS362E37NPL")))
        }
        findViewById<MaterialButton>(R.id.btnSubmitAdvertiserTask).setOnClickListener {
            submitRequest(findViewById<EditText>(R.id.etAdvertiserProof).text.toString(), "AD_LISTING_450")
        }
    }

    private fun submitRequest(proof: String, type: String) {
        val user = auth.currentUser ?: return
        if (proof.isEmpty()) return
        val data = mapOf("email" to user.email, "uid" to user.uid, "proof" to proof, "status" to "pending", "type" to type)
        database.child("admin_verifications").push().setValue(data).addOnSuccessListener { finish() }
    }

    private fun loadNextAd() {
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
        })
    }
}
