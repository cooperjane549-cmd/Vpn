package com.sweetdata.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import libv2ray.Libv2ray
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var isRunning = false
    
    // VPS Configuration
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "sweetdata_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // 1. FOREGROUND PROTECT: Prevents Android from killing the service
        startServiceForeground()

        // 2. FETCH CLOUD BUG: Get the string passed from MainActivity
        val bugHost = intent?.getStringExtra("BUG_HOST") ?: "biladata.safaricom.co.ke"
        
        if (!isRunning) {
            // Run in a thread so the UI doesn't freeze
            Thread { setupAndStartVpn(bugHost) }.start()
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
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SweetData VPN is Active")
            .setContentText("Optimizing your network path...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun setupAndStartVpn(bugHost: String) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("SweetData VPN")
                .setMtu(1280) 
                .addAddress("172.19.0.1", 30)
                .addDnsServer("8.8.8.8") 
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0) 
                
                // NO INTERNET FIX:
                // 1. Don't tunnel the VPN app itself
                .addDisallowedApplication(packageName) 
                // 2. Bypass the VPS IP so the core can reach the server
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
                  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                }
              }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        isRunning = false
        coreController?.stopLoop()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
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
