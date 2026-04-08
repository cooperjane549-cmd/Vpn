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
    
    // Server Config - Verified from your 'cat' command
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "V2Ray Status: $msg")
            return 0
        }
        override fun startup(): Long {
            isRunning = true
            return 0
        }
        override fun shutdown(): Long {
            isRunning = false
            return 0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            setupAndStartVpn()
        }
        return START_STICKY
    }

    private fun setupAndStartVpn() {
        try {
            // 1. Initialize Core
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            // 2. Build the VPN Tunnel
            val builder = Builder()
                .setSession("SweetData VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) 
                .addDnsServer("1.1.1.1")
                .setMtu(1400)
                .addDisallowedApplication(packageName) 

            // Prevent loop by excluding the VPS IP
            builder.addRoute(vpsIp, 32)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                
                // 3. Start Core in a Background Thread
                Thread {
                    try {
                        val config = generateVlessConfig("m-pesaforbusiness.co.ke")
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        Libv2ray.touch()
                        coreController?.startLoop(config, fd)
                    } catch (e: Exception) {
                        Log.e("SweetData", "Core Loop Error: ${e.message}")
                        stopSelf()
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Setup Crash: ${e.message}")
            stopSelf()
        }
    }

    private fun generateVlessConfig(bug: String): String {
        // Matches your VPS: Port 443, Security: none, Network: ws, Path: /sweetdata
        return """
        {
          "log": { "loglevel": "warning" },
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "$vpsIp", 
                "port": 443, 
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
                  "Host": "$bug"
                } 
              }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        isRunning = false
        try { coreController?.stopLoop() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
