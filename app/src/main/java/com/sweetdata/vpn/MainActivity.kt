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
    
    private var isVpnRunning = false
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val PREFS_NAME = "SweetDataPrefs"
    private val handler = Handler(Looper.getMainLooper())
    
    private val timeUpdater = object : Runnable {
        override fun run() {
            updateBalanceUI()
            handler.postDelayed(this, 10000) 
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase & Google Auth
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // UI Bindings
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvBalance = findViewById(R.id.tvMbBalance)
        toggleNetwork = findViewById(R.id.toggleNetworkGroup)

        // Network Selection (Kevin, Sherwin, Blue)
        toggleNetwork.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val colorCode = when (checkedId) {
                    R.id.btnSafaricom -> "GREEN" // Kevin
                    R.id.btnAirtel -> "RED"      // Sherwin
                    R.id.btnTelkom -> "BLUE"     // Blue
                    else -> "GREEN"
                }
                prefs.edit().putString("selected_network_color", colorCode).apply()
                Toast.makeText(this, "Mode: $colorCode", Toast.LENGTH_SHORT).show()
            }
        }

        // Navigation
        findViewById<MaterialButton>(R.id.navTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        // Connect Button
        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpn()
            } else {
                validateAndConnect()
            }
        }

        // Auth Gate
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            signInWithGoogle()
        } else {
            runSafetyChecks()
            syncTimeFromFirebase()
        }

        MobileAds.initialize(this) {}
        handler.post(timeUpdater)
    }

    private fun validateAndConnect() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry_time", 0)
        
        if (System.currentTimeMillis() < expiry) {
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, 102)
            } else {
                executeVpnStart()
            }
        } else {
            Toast.makeText(this, "ACCESS EXPIRED!", Toast.LENGTH_LONG).show()
        }
    }

    private fun executeVpnStart() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val color = prefs.getString("selected_network_color", "GREEN") ?: "GREEN"

            val intent = Intent(this, MyVpnService::class.java)
            intent.putExtra("NETWORK_COLOR", color)
            
            startService(intent)
            
            isVpnRunning = true
            btnConnect.text = "STOP"
            // Keeping your Red Color
            btnConnect.setBackgroundColor(Color.parseColor("#FF0033"))
            tvStatus.text = "CONNECTING ($color)..."
        } catch (e: Exception) {
            Log.e("SweetData", "Start Error: ${e.message}")
            Toast.makeText(this, "Engine failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, MyVpnService::class.java)
        intent.action = "STOP"
        startService(intent)
        
        isVpnRunning = false
        btnConnect.text = "CONNECT"
        // Return to your Red Color
        btnConnect.setBackgroundColor(Color.parseColor("#FF0033"))
        updateBalanceUI()
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
            tvStatus.text = "PREMIUM ACTIVE"
            tvBalance.text = "$mins MINUTES LEFT"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            tvStatus.text = "ACCESS EXPIRED"
            tvBalance.text = "0 MIN"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            if (isVpnRunning) stopVpn()
        }
    }

    private fun runSafetyChecks() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            AlertDialog.Builder(this)
                .setTitle("Terms of Service")
                .setMessage("Use at your own risk.")
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
            if (task.isSuccessful) {
                runSafetyChecks()
                syncTimeFromFirebase()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            executeVpnStart()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeUpdater)
    }
}
