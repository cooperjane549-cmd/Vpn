package com.sweetdata.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import libv2ray.Libv2ray
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var isRunning = false
    
    // VPS Configuration - Locked to Port 80 based on your server check
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "sweetdata_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "STOP" || action == "STOP_SERVICE") {
            stopVpn()
            return START_NOT_STICKY
        }

        startServiceForeground()

        // --- FIREBASE REMOTE CONFIG FETCH ---
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // Fetches every hour
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            // Pulls "bug_host" from Firebase, defaults to biladata if fetch fails
            val bugHost = if (task.isSuccessful) {
                remoteConfig.getString("bug_host") 
            } else {
                "biladata.safaricom.co.ke"
            }

            if (!isRunning) {
                Thread { 
                    try {
                        setupAndStartVpn(bugHost) 
                    } catch (e: Exception) {
                        Log.e("SweetData", "Core Thread Error: ${e.message}")
                    }
                }.start()
            }
        }
        
        return START_STICKY
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SweetData Active Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SweetData VPN is Active")
            .setContentText("Optimizing your network path...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun setupAndStartVpn(bugHost: String) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("SweetData VPN")
                // FIX: MTU 1100 for better stability on Safaricom/Airtel
                .setMtu(1100) 
                // FIX: Standard subnet to prevent routing black holes
                .addAddress("10.0.0.2", 24) 
                .addDnsServer("1.1.1.1") 
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                
                .addDisallowedApplication(packageName) 
                .addRoute(vpsIp, 32) 

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                val config = generateXrayConfig(bugHost)
                
                coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                    override fun onEmitStatus(s: Long, m: String?): Long = 0
                    override fun startup(): Long { isRunning = true; return 0 }
                    override fun shutdown(): Long { isRunning = false; return 0 }
                })
                
                coreController?.startLoop(config, fd)
            } else {
                Log.e("SweetData", "VPN Interface could not be established")
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Setup Crash: ${e.message}")
        }
    }

    private fun generateXrayConfig(host: String): String {
        return """
        {
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "$vpsIp",
                "port": 80,
                "users": [{ 
                    "id": "$vlessUuid", 
                    "encryption": "none",
                    "level": 0
                }]
              }]
            },
            "streamSettings": {
              "network": "ws",
              "security": "none",
              "wsSettings": {
                "path": "/sweetdata",
                "headers": { 
                  "Host": "$host",
                  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }
              }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        try {
            isRunning = false
            coreController?.stopLoop()
            vpnInterface?.close()
            vpnInterface = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) {
            Log.e("SweetData", "Error stopping service: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
