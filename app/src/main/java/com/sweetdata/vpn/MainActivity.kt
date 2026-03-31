package com.sweetdata.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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

        // 1. Initialize Firebase (Automatic via google-services.json)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        // 2. Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // This comes from the JSON plugin
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 3. Link UI
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        // 4. Network Toggle Logic (Kevin/Sherwin Alignment)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val internalName = if (checkedId == R.id.btnSafaricom) "Safaricom" else "Airtel"
                val displayName = if (checkedId == R.id.btnSafaricom) "Kevin" else "Sherwin"
                
                prefs.edit().putString("selected_network", internalName).apply()
                Toast.makeText(this, "Network: $displayName", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Navigation
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        // 6. VPN Connect Logic
        btnConnect.setOnClickListener {
            if (isVpnRunning) stopVpn() else checkAccessAndStart()
        }

        // 7. Check Login Status
        if (auth.currentUser == null) {
            signInWithGoogle()
        } else {
            syncTimeFromFirebase()
        }

        MobileAds.initialize(this) {}
        loadInterstitial()
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
            } catch (e: ApiException) {
                Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                syncTimeFromFirebase()
            }
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

    private fun checkAccessAndStart() {
        val expiry = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("expiry_time", 0)
        if (System.currentTimeMillis() < expiry) {
            startVpnProcess()
        } else {
            Toast.makeText(this, "No time left! Get bundles in TASKS", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpnProcess() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 102)
        else onActivityResult(102, RESULT_OK, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
            isVpnRunning = true
            btnConnect.text = "STOP"
            tvStatus.text = "CONNECTED"
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply { action = "STOP" })
        isVpnRunning = false
        btnConnect.text = "CONNECT"
        updateBalanceUI()
    }

    private fun loadInterstitial() {
        InterstitialAd.load(this, "ca-app-pub-2344867686796379/4612206920", AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
            })
    }
}
