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
    
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "sweetdata_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // 1. IMMEDIATE NOTIFICATION (Prevents Android 14 crash)
        startServiceForeground()

        // 2. FETCH CONFIG IN BACKGROUND
        Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener { 
            val bugHost = Firebase.remoteConfig.getString("bug_host").ifEmpty { "www.google.com" }
            if (!isRunning) {
                Thread { safeStartVpn(bugHost) }.start()
            }
        }
        
        return START_STICKY
    }

    private fun safeStartVpn(host: String) {
        try {
            // Setup internal files
            val assetPath = File(filesDir, "assets").apply { mkdirs() }.absolutePath
            Libv2ray.initCoreEnv(assetPath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("SweetData")
                .setMtu(1280) // 1280 is the safest for Airtel/Safaricom bundles
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                .addDisallowedApplication(packageName) // PREVENTS BUNDLE KILL (The VPN stays outside)

            vpnInterface = builder.establish() ?: return
            
            val config = generateXrayConfig(host)
            
            coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun onEmitStatus(s: Long, m: String?): Long = 0
                override fun startup(): Long { isRunning = true; return 0 }
                override fun shutdown(): Long { isRunning = false; return 0 }
            })

            // Attempt to start the core
            coreController?.startLoop(config, vpnInterface!!.fd)

        } catch (e: Exception) {
            Log.e("SweetData", "Core Error: ${e.message}")
            stopVpn()
        }
    }

    private fun generateXrayConfig(host: String): String {
        return """
        {
          "dns": { "servers": ["8.8.8.8", "1.1.1.1"] },
          "outbounds": [{
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
              "wsSettings": { "path": "/sweetdata", "headers": { "Host": "$host" } }
            }
          }]
        }
        """.trimIndent()
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SweetData VPN")
            .setContentText("Connected & Secure")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true).build())
    }

    private fun stopVpn() {
        isRunning = false
        coreController?.stopLoop()
        vpnInterface?.close()
        stopSelf()
    }
}
