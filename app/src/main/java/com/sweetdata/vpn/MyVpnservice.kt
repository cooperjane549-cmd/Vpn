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
    private val CHANNEL_ID = "matcha_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startServiceForeground()

        Thread {
            try {
                Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener { 
                    val bugHost = Firebase.remoteConfig.getString("bug_host").ifEmpty { "biladata.safaricom.com" }
                    val wsPath = Firebase.remoteConfig.getString("ws_path").ifEmpty { "/sweetdata" }
                    
                    if (!isRunning) {
                        runVpnCore(bugHost, wsPath)
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN_BUILD", "Error: ${e.message}")
            }
        }.start()
        
        return START_STICKY
    }

    private fun runVpnCore(host: String, path: String) {
        try {
            // FIXED: Removed the 3rd parameter to match your library
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("Matcha VPN")
                .setMtu(1280)
                .addAddress("10.0.1.1", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                .addDisallowedApplication(packageName)

            vpnInterface = builder.establish() ?: return
            val fd = vpnInterface!!.fd

            val v2rayConfig = """
            {
              "log": { "loglevel": "none" },
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
                    "wsSettings": { "path": "$path", "headers": { "Host": "$host" } }
                  }
              }]
            }
            """.trimIndent()

            coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun onEmitStatus(s: Long, m: String?): Long = 0
                override fun startup(): Long { isRunning = true; return 0 }
                override fun shutdown(): Long { isRunning = false; return 0 }
            })

            coreController?.startLoop(v2rayConfig, fd)

        } catch (e: Exception) {
            Log.e("VPN_CORE", "Failure: ${e.message}")
            stopVpn()
        }
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Matcha VPN")
            .setContentText("Protecting your connection...")
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
}
