package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class TasksActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://sweetdatavpn-default-rtdb.firebaseio.com/").reference
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        MobileAds.initialize(this)
        loadAd()

        // Setup RecyclerView for SproutGigs style list
        val rv = findViewById<RecyclerView>(R.id.rvActiveTasks)
        rv.layoutManager = LinearLayoutManager(this)

        // --- ADVERTISER: POST JOB ---
        findViewById<Button>(R.id.btnSubmitAdRequest).setOnClickListener {
            val title = findViewById<EditText>(R.id.etAdTitle).text.toString().trim()
            val link = findViewById<EditText>(R.id.etAdLink).text.toString().trim()
            val proof = findViewById<EditText>(R.id.etPaymentProof).text.toString().trim()

            if (title.isNotEmpty() && proof.isNotEmpty()) {
                val jobData = mapOf(
                    "uid" to (auth.currentUser?.uid ?: ""),
                    "title" to title,
                    "link" to link,
                    "proof_sent" to proof,
                    "type" to "JOB_POST_REQUEST"
                )
                database.child("admin_verifications").push().setValue(jobData)
                    .addOnSuccessListener { 
                        Toast.makeText(this, "Job Posted! Waiting for Admin.", Toast.LENGTH_SHORT).show() 
                    }
            }
        }

        // --- WORKER: WATCH ADS ---
        findViewById<Button>(R.id.btnWatchAd).setOnClickListener {
            mInterstitialAd?.show(this) ?: loadAd()
        }
        
        // --- ADVERTISER: PAY BUTTONS ---
        findViewById<Button>(R.id.btnPayMpesa).setOnClickListener {
            Toast.makeText(this, "Till: 3043489", Toast.LENGTH_LONG).show()
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
