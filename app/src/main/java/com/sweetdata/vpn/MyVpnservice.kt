package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.Libv2ray
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var isRunning = false
    
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    
    // THE BUG: Using the free site from your screenshot
    private val SNI_BUG = "biladata.safaricom.co.ke"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!isRunning) setupAndStartVpn()
        return START_STICKY
    }

    private fun setupAndStartVpn() {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            val builder = Builder()
                .setSession("SweetData VPN")
                .setMtu(1400) // Lower MTU is better for Safaricom signal stability
                .addAddress("172.19.0.1", 30)
                .addDnsServer("8.8.8.8") 
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
            
            // Bypass VPS IP so the core can reach the server
            builder.addRoute(vpsIp, 32)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                Thread {
                    try {
                        val config = generateV2rayConfig()
                        coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                            override fun onEmitStatus(status: Long, msg: String?): Long = 0
                            override fun startup(): Long { isRunning = true; return 0 }
                            override fun shutdown(): Long { isRunning = false; return 0 }
                        })
                        coreController?.startLoop(config, fd)
                    } catch (e: Exception) {
                        Log.e("SweetData", "Core Loop Error: ${e.message}")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Setup Crash: ${e.message}")
        }
    }

    private fun generateV2rayConfig(): String {
        return """
        {
          "log": { "loglevel": "warning" },
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "$vpsIp",
                "port": 443,
                "users": [{ "id": "$vlessUuid", "encryption": "none" }]
              }]
            },
            "streamSettings": {
              "network": "ws",
              "security": "none",
              "wsSettings": {
                "path": "/sweetdata",
                "headers": {
                  "Host": "$SNI_BUG",
                  "User-Agent": "Mozilla/5.0 (Linux; Android 10; Mobile) Chrome/120.0.0.0"
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
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
