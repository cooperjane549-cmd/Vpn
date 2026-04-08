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
    
    // VPS Details
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // FIX: Match the key from MainActivity ("NETWORK_COLOR")
        val selectedColor = intent?.getStringExtra("NETWORK_COLOR") ?: "GREEN"

        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        
        if (!isRunning) {
            setupAndStartVpn(selectedColor)
        }
        return START_STICKY
    }

    private fun setupAndStartVpn(color: String) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            // Select the bug based on the color sent from UI
            val bugHost = when (color.uppercase()) {
                "RED" -> "africanstorybook.org"    // Sherwin (Airtel)
                "BLUE" -> "stats.mwalimuplus.com"  // Blue (Telkom)
                "GREEN" -> "biladata.safaricom.co.ke" // Kevin (Safaricom)
                else -> "biladata.safaricom.co.ke"
            }

            val builder = Builder()
                .setSession("SweetData VPN")
                .setMtu(1280) // 1280 is best for 4G stability
                .addAddress("172.19.0.1", 30)
                .addDnsServer("1.1.1.1") 
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
                .addRoute(vpsIp, 32) // Keep VPS traffic outside the tunnel

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                Thread {
                    try {
                        val config = generateConfig(bugHost)
                        coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
                            override fun onEmitStatus(s: Long, m: String?): Long = 0
                            override fun startup(): Long { isRunning = true; return 0 }
                            override fun shutdown(): Long { isRunning = false; return 0 }
                        })
                        coreController?.startLoop(config, fd)
                    } catch (e: Exception) {
                        Log.e("SweetData", "Core Error: ${e.message}")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Setup Crash: ${e.message}")
        }
    }

        private fun generateConfig(host: String): String {
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
                  "User-Agent": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
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
}
