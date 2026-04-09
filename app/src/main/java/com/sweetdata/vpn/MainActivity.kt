package com.sweetdata.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.graphics.Color
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
    
    // --- FIREBASE BUG VARIABLES ---
    private var airtelBug = "www.airtelkenya.com"
    private var safaricomBug = "biladata.safaricom.co.ke"
    private var telkomBug = "www.telkom.co.ke"
    
    private val PREFS_NAME = "SweetDataPrefs"
    private val handler = Handler(Looper.getMainLooper())
    
    private val timeUpdater = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                updateBalanceUI()
                handler.postDelayed(this, 10000) 
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        // --- START BUG LISTENER ---
        listenForBugUpdates()
        
        // Safety check for Web Client ID - Prevents crash if string is missing
        val clientId = try {
            val id = getString(R.string.default_web_client_id)
            if (id.isNullOrEmpty()) "placeholder_client_id" else id
        } catch (e: Exception) {
            Log.e("SweetData", "Missing Web Client ID in strings.xml")
            "placeholder_client_id"
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (prefs.getString("selected_network_color", null) == null) {
            prefs.edit().putString("selected_network_color", "GREEN").apply()
        }

        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val colorCode = when (checkedId) {
                    R.id.btnSafaricom -> "GREEN"
                    R.id.btnAirtel -> "RED"
                    R.id.btnTelkom -> "BLUE"
                    else -> "GREEN"
                }
                prefs.edit().putString("selected_network_color", colorCode).apply()
            }
        }

        // --- NAVIGATION BUTTONS ---
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        btnConnect.setOnClickListener {
            if (isVpnRunning) stopVpn() else validateAndConnect()
        }

        val user = auth.currentUser
        if (user == null) {
            signInWithGoogle()
        } else {
            runSafetyChecks()
            syncTimeFromFirebase()
        }

        MobileAds.initialize(this) {}
        loadInterstitial()
        handler.post(timeUpdater)
    }

    private fun listenForBugUpdates() {
        database.child("settings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                airtelBug = snapshot.child("airtel_bug").getValue(String::class.java) ?: airtelBug
                safaricomBug = snapshot.child("safaricom_bug").getValue(String::class.java) ?: safaricomBug
                telkomBug = snapshot.child("telkom_bug").getValue(String::class.java) ?: telkomBug
                Log.d("SweetData", "Bugs Synced: $airtelBug | $safaricomBug | $telkomBug")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    mInterstitialAd = null
                    handler.postDelayed({ loadInterstitial() }, 30000)
                }
            })
    }

    private fun validateAndConnect() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        
        if (System.currentTimeMillis() < expiry || expiry == 0L) { 
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, 102)
            } else {
                startVpnFlow()
            }
        } else {
            Toast.makeText(this, "ACCESS EXPIRED!", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpnFlow() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    executeVpnStart()
                    mInterstitialAd = null
                    loadInterstitial() 
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    executeVpnStart()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            executeVpnStart()
        }
    }

    private fun executeVpnStart() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val color = prefs.getString("selected_network_color", "GREEN") ?: "GREEN"

            val finalBugHost = when(color.uppercase()) {
                "RED" -> airtelBug
                "BLUE" -> telkomBug
                else -> safaricomBug
            }

            // --- CRASH FIX: Intent Optimization ---
            val intent = Intent(this, MyVpnService::class.java).apply {
                putExtra("NETWORK_COLOR", color)
                putExtra("BUG_HOST", finalBugHost)
            }
            
            // Using applicationContext for foreground service stability
            ContextCompat.startForegroundService(applicationContext, intent)
            
            isVpnRunning = true
            btnConnect.text = "STOP"
            btnConnect.setBackgroundColor(Color.parseColor("#4CAF50")) 
            tvStatus.text = "TUNNELING ($color)..."
        } catch (e: Exception) {
            Log.e("SweetData", "Start Error: ${e.message}")
            Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpn() {
        // --- CRASH FIX: Standardized Action ---
        val intent = Intent(this, MyVpnService::class.java).apply { 
            action = "STOP_SERVICE" 
        }
        startService(intent)
        isVpnRunning = false
        btnConnect.text = "CONNECT"
        btnConnect.setBackgroundColor(Color.parseColor("#FF0033")) 
        tvStatus.text = "DISCONNECTED"
        updateBalanceUI()
    }

    private fun syncTimeFromFirebase() {
        val userId = auth.currentUser?.uid ?: return
        database.child("users").child(userId).child("expiry_time")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFinishing && !isDestroyed) {
                        val expiry = snapshot.getValue(Long::class.java) ?: 0L
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putLong("expiry_time", expiry).apply()
                        updateBalanceUI()
                    }
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
            tvStatus.text = "PREMIUM ACTIVE"
            tvBalance.text = "$mins MINUTES LEFT"
            tvStatus.setTextColor(Color.GREEN)
        } else if (expiry != 0L) {
            tvStatus.text = "ACCESS EXPIRED"
            tvBalance.text = "0 MIN"
            tvStatus.setTextColor(Color.RED)
            if (isVpnRunning) stopVpn()
        }
    }

    private fun runSafetyChecks() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            AlertDialog.Builder(this)
                .setTitle("SweetData VPN")
                .setMessage("By using this app, you agree that SweetData is not liable for data loss or network interruptions.")
                .setCancelable(false)
                .setPositiveButton("Accept") { _, _ -> 
                    prefs.edit().putBoolean("terms_accepted", true).apply()
                    checkBatteryExemption() 
                }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .show()
        } else {
            checkBatteryExemption()
        }
    }

    private fun checkBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {}
        }
    }

    private fun signInWithGoogle() {
        try {
            val signInIntent = googleSignInClient.signInIntent
            googleLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e("SweetData", "Auth Launch Error: ${e.message}")
        }
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) { 
                Log.e("SweetData", "Google Sign-In Fail")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                runSafetyChecks()
                syncTimeFromFirebase()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startVpnFlow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeUpdater)
    }
}
