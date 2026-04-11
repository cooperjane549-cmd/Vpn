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
import libv2ray.Libv2ray
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler
import java.io.File

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var isRunning = false
    
    // Core Server Details (Hardcoded for testing - move to Firebase later)
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "matcha_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // 1. Start Notification immediately to satisfy Android 14+
        startServiceForeground()

        // 2. Run the connection in a background thread
        Thread {
            try {
                // Fetch latest bug from Firebase
                val remoteConfig = Firebase.remoteConfig
                remoteConfig.fetchAndActivate().addOnCompleteListener { 
                    val bugHost = remoteConfig.getString("bug_host").ifEmpty { "www.airtel.com.ke" }
                    val wsPath = remoteConfig.getString("ws_path").ifEmpty { "/sweetdata" }
                    
                    if (!isRunning) {
                        runVpnCore(bugHost, wsPath)
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN_FATAL", "Thread error: ${e.message}")
            }
        }.start()
        
        return START_STICKY
    }

    private fun runVpnCore(host: String, path: String) {
        try {
            // Setup Environment - Note the 3rd empty string (Required by many 2026 libs)
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath, "")

            val builder = Builder()
                .setSession("Matcha VPN")
                .setMtu(1280) // Crucial for Airtel/Safaricom bundles
                .addAddress("10.0.1.1", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                .addDisallowedApplication(packageName) // Prevents the 0-byte routing loop

            vpnInterface = builder.establish() ?: return
            val fd = vpnInterface!!.fd

            // Build the Xray Config
            val v2rayConfig = """
            {
              "log": { "loglevel": "none" },
              "inbounds": [{
                "port": 10808, "protocol": "socks", "settings": { "auth": "noauth" }
              }],
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [{
                      "address": "$vpsIp",
                      "port": 80,
                      "users": [{ "id": "$vlessUuid", "encryption": "none" }]
                    }]
                  },
                  "streamSettings": {
                    "network": "ws",
                    "wsSettings": { "path": "$path", "headers": { "Host": "$host" } }
                  },
                  "tag": "proxy"
                }
              ]
            }
            """.trimIndent()

            coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun onEmitStatus(s: Long, m: String?): Long = 0
                override fun startup(): Long { isRunning = true; return 0 }
                override fun shutdown(): Long { isRunning = false; return 0 }
            })

            // This is the bridge. If the FD isn't accepted here, you get 0 bytes.
            coreController?.startLoop(v2rayConfig, fd)

        } catch (e: Exception) {
            Log.e("VPN_CORE", "Crash: ${e.message}")
            stopVpn()
        }
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Matcha VPN")
            .setContentText("Connected to Airtel Network")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
            
        startForeground(1, notification)
    }

    private fun stopVpn() {
        isRunning = false
        coreController?.stopLoop()
        vpnInterface?.close()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
