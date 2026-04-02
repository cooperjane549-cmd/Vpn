package com.sweetdata.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvBalance: TextView
    private lateinit var toggleNetwork: MaterialButtonToggleGroup
    
    private var mInterstitialAd: InterstitialAd? = null
    private var isVpnRunning = false
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val PREFS_NAME = "SweetDataPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        // Mandatory Terms & Conditions Check
        checkTermsAndConditions()

        // Battery Optimization Bypass
        requestBatteryExemption()

        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val internalName = if (checkedId == R.id.btnSafaricom) "Safaricom" else "Airtel"
                prefs.edit().putString("selected_network", internalName).apply()
            }
        }

        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        btnConnect.setOnClickListener {
            if (isVpnRunning) stopVpn() else validateAndConnect()
        }

        if (auth.currentUser == null) signInWithGoogle() else syncTimeFromFirebase()

        // Initialize Ads
        MobileAds.initialize(this) {}
        loadInterstitial() 
    }

    private fun checkTermsAndConditions() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            AlertDialog.Builder(this)
                .setTitle("Terms of Service")
                .setMessage("By using SweetData VPN, you agree to our terms. We do not support illegal use. This app optimizes your network via a secure tunnel.")
                .setCancelable(false)
                .setPositiveButton("Accept") { _, _ ->
                    prefs.edit().putBoolean("terms_accepted", true).apply()
                }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .show()
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Keep SweetData Alive")
                .setMessage("To ensure 24/7 connectivity, please allow SweetData to run without battery restrictions.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }.show()
        }
    }

    private fun validateAndConnect() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selected = prefs.getString("selected_network", "Safaricom")
        
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val currentCarrier = try { 
            telephonyManager.networkOperatorName.ifEmpty { "Unknown Network" } 
        } catch(e: Exception) { 
            "Unknown" 
        }

        // We inform the user but we NO LONGER block them. 
        Toast.makeText(this, "Connecting via $currentCarrier ($selected)", Toast.LENGTH_SHORT).show()
        checkAccessAndStart()
    }

    private fun checkAccessAndStart() {
        val expiry = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("expiry_time", 0)
        if (System.currentTimeMillis() < expiry) {
            val intent = android.net.VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, 102)
            else onActivityResult(102, RESULT_OK, null)
        } else {
            Toast.makeText(this, "No time left! Get bundles in TASKS", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncTimeFromFirebase() {
        val userId = auth.currentUser?.uid ?: return
        database.child("users").child(userId).child("expiry_time")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val expiry = snapshot.getValue(Long::class.java) ?: 0L
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong("expiry_time", expiry).apply()
                    updateBalanceUI()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateBalanceUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        val diff = expiry - System.currentTimeMillis()

        if (diff > 0) {
            val mins = diff / (60 * 1000)
            tvStatus.text = "ACTIVE"
            tvBalance.text = "$mins MIN"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        } else {
            tvStatus.text = "DISCONNECTED"
            tvBalance.text = "0 MIN"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
            isVpnRunning = true
            btnConnect.text = "STOP"
            tvStatus.text = "CONNECTED"
            
            if (mInterstitialAd != null) {
                mInterstitialAd?.show(this)
            } else {
                loadInterstitial() 
            }
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply { action = "STOP" })
        isVpnRunning = false
        btnConnect.text = "CONNECT"
        updateBalanceUI()
        loadInterstitial() 
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            mInterstitialAd = null
                            loadInterstitial() 
                        }
                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            mInterstitialAd = null
                            loadInterstitial()
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    mInterstitialAd = null
                    // Retry after 15 seconds if failed
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadInterstitial()
                    }, 15000)
                }
            })
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleLauncher.launch(signInIntent)
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) { }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) syncTimeFromFirebase()
        }
    }
}
