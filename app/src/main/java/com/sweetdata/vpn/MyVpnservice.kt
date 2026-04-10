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
    
    // VPS Configuration - Matches your cat /usr/local/etc/xray/config.json
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val CHANNEL_ID = "matcha_vpn_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // Handle stop signal from MainActivity
        if (action == "STOP" || action == "STOP_SERVICE") {
            stopVpn()
            return START_NOT_STICKY
        }

        // 1. Start Foreground immediately to satisfy Android System requirements
        startServiceForeground()

        // 2. Fetch the Bug Host (e.g., biladata.safaricom.co.ke)
        val bugHost = intent?.getStringExtra("BUG_HOST") ?: "biladata.safaricom.co.ke"
        
        if (!isRunning) {
            Thread { 
                try {
                    setupAndStartVpn(bugHost) 
                } catch (e: Exception) {
                    Log.e("MatchaVPN", "Core Thread Error: ${e.message}")
                }
            }.start()
        }
        
        return START_STICKY
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Matcha VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Matcha is Connecting")
            .setContentText("Routing your traffic through $vpsIp")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun setupAndStartVpn(bugHost: String) {
        try {
            // Initialize LibV2Ray environment
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("Matcha VPN")
                // --- FIX 1: MTU 1100 for Carrier Compatibility ---
                .setMtu(1100) 
                // --- FIX 2: Standard Subnet for Routing stability ---
                .addAddress("10.0.0.2", 24) 
                // --- FIX 3: Dual DNS to avoid local blocking ---
                .addDnsServer("1.1.1.1") 
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0) 
                
                // Prevent the app from tunneling itself (Infinite Loop Fix)
                .addDisallowedApplication(packageName) 
                // Bypass the VPS IP so the tunnel connection itself stays outside the VPN
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
            Log.e("MatchaVPN", "VPN Setup Failure: ${e.message}")
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
                    "encryption": "none"
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
            Log.e("MatchaVPN", "Error during stop: ${e.message}")
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
