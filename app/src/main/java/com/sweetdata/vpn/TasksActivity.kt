package com.sweetdata.vpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

        // 1. Initialize Ads
        MobileAds.initialize(this)
        loadAd()

        // 2. Setup Job List (RecyclerView)
        val rvActiveTasks = findViewById<RecyclerView>(R.id.rvActiveTasks)
        rvActiveTasks.layoutManager = LinearLayoutManager(this)
        setupJobList(rvActiveTasks)

        // 3. Worker: Watch Ad Button
        findViewById<Button>(R.id.btnWatchAd).setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd?.show(this)
            } else {
                Toast.makeText(this, "Ad is loading, please wait...", Toast.LENGTH_SHORT).show()
                loadAd()
            }
        }

        // 4. Advertiser: Payment Buttons
        findViewById<Button>(R.id.btnPayMpesa).setOnClickListener {
            Toast.makeText(this, "Pay Kes 450 to Till: 3043489", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnPayPaypal).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/ncp/payment/E9WS362E37NPL"))
            startActivity(intent)
        }

        // 5. Advertiser: Post Job Button
        findViewById<Button>(R.id.btnSubmitAdRequest).setOnClickListener {
            val title = findViewById<EditText>(R.id.etAdTitle).text.toString().trim()
            val link = findViewById<EditText>(R.id.etAdLink).text.toString().trim()
            val proof = findViewById<EditText>(R.id.etPaymentProof).text.toString().trim()

            if (title.isEmpty() || proof.isEmpty()) {
                Toast.makeText(this, "Please enter Title and Payment Proof", Toast.LENGTH_SHORT).show()
            } else {
                submitAdRequest(title, link, proof)
            }
        }
    }

    // --- FIREBASE ADAPTER LOGIC ---
    private fun setupJobList(recyclerView: RecyclerView) {
        val jobList = mutableListOf<Job>()
        
        // Listen for approved jobs from your database
        database.child("approved_jobs").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                jobList.clear()
                for (data in snapshot.children) {
                    val job = data.getValue(Job::class.java)
                    if (job != null) jobList.add(job)
                }
                
                // Simple Adapter inline
                recyclerView.adapter = object : RecyclerView.Adapter<JobViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
                        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
                        return JobViewHolder(view)
                    }

                    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
                        val currentJob = jobList[position]
                        holder.title.text = currentJob.title
                        holder.btnDo.setOnClickListener {
                            showJobPopup(currentJob.title, currentJob.link, currentJob.id)
                        }
                    }

                    override fun getItemCount() = jobList.size
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- THE WORKER POPUP (Submit Proof) ---
    private fun showJobPopup(title: String, link: String, jobId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_submit_proof, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogJobTitle)
        val btnLink = dialogView.findViewById<Button>(R.id.btnGoToLink)
        val etProof = dialogView.findViewById<EditText>(R.id.etUserProof)

        tvTitle.text = title
        btnLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Complete Task")
            .setPositiveButton("Submit") { _, _ ->
                val proofText = etProof.text.toString().trim()
                if (proofText.isNotEmpty()) {
                    sendWorkToAdmin(proofText, title, jobId)
                } else {
                    Toast.makeText(this, "Proof is required!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendWorkToAdmin(proof: String, jobTitle: String, jobId: String) {
        val user = auth.currentUser ?: return
        val workData = mapOf(
            "workerEmail" to (user.email ?: ""),
            "workerUid" to user.uid,
            "jobTitle" to jobTitle,
            "jobId" to jobId,
            "proofText" to proof,
            "status" to "pending",
            "type" to "WORKER_SUBMISSION",
            "timestamp" to System.currentTimeMillis()
        )
        database.child("admin_verifications").push().setValue(workData)
            .addOnSuccessListener {
                Toast.makeText(this, "Proof sent! Reward coming soon.", Toast.LENGTH_LONG).show()
            }
    }

    private fun submitAdRequest(title: String, link: String, proof: String) {
        val user = auth.currentUser ?: return
        val adData = mapOf(
            "email" to user.email,
            "uid" to user.uid,
            "jobTitle" to title,
            "jobLink" to link,
            "paymentProof" to proof,
            "status" to "pending",
            "type" to "AD_POST_REQUEST",
            "timestamp" to System.currentTimeMillis()
        )
        database.child("admin_verifications").push().setValue(adData)
            .addOnSuccessListener {
                Toast.makeText(this, "Job request sent to Admin!", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun loadAd() {
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(p0: LoadAdError) { mInterstitialAd = null }
            })
    }

    class JobViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvJobTitle)
        val btnDo: Button = v.findViewById(R.id.btnDoJob)
    }
}
