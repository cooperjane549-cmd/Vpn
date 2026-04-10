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
    
    // Core Server Details
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "sweetdata_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startServiceForeground()

        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = 300 // Check for new bugs every 5 mins
        })

        remoteConfig.fetchAndActivate().addOnCompleteListener { 
            // Pulling dynamic values from Firebase Settings
            val bugHost = remoteConfig.getString("bug_host").ifEmpty { "www.google.com" }
            val wsPath = remoteConfig.getString("ws_path").ifEmpty { "/sweetdata" }
            val networkMtu = remoteConfig.getLong("mtu").toInt().let { if (it == 0) 1280 else it }

            if (!isRunning) {
                Thread { setupAndStartVpn(bugHost, wsPath, networkMtu) }.start()
            }
        }
        
        return START_STICKY
    }

    private fun setupAndStartVpn(host: String, path: String, mtuValue: Int) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("SweetData Universal")
                .setMtu(mtuValue) 
                .addAddress("10.0.0.2", 24) 
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                .addDisallowedApplication(packageName) 

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val config = generateXrayConfig(host, path)
                coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                    override fun onEmitStatus(s: Long, m: String?): Long = 0
                    override fun startup(): Long { isRunning = true; return 0 }
                    override fun shutdown(): Long { isRunning = false; return 0 }
                })
                coreController?.startLoop(config, vpnInterface!!.fd)
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Fatal: ${e.message}")
        }
    }

    private fun generateXrayConfig(host: String, path: String): String {
        return """
        {
          "dns": { "servers": ["8.8.8.8", "1.1.1.1"] },
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
                "security": "none",
                "wsSettings": {
                  "path": "$path",
                  "headers": { "Host": "$host" }
                }
              },
              "tag": "proxy"
            },
            { "protocol": "freedom", "tag": "direct" }
          ],
          "routing": {
            "rules": [{ "type": "field", "outboundTag": "proxy", "port": "0-65535" }]
          }
        }
        """.trimIndent()
    }

    private fun startServiceForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SweetData is Online")
            .setContentText("Protecting your connection...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        startForeground(1, notification)
    }

    private fun stopVpn() {
        isRunning = false
        coreController?.stopLoop()
        vpnInterface?.close()
        stopSelf()
    }
}
