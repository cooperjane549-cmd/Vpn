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

    // HARDCODED CONFIG - To eliminate variable errors
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"
    private val SNI_BUG = "m-pesaforbusiness.co.ke"

    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "Core Status: $msg")
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
            // 1. Initialize environment
            Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

            // 2. Build the VPN Tunnel with strict routing
            val builder = Builder()
                .setSession("SweetData VPN")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30) // VPN Internal IP
                .addDnsServer("8.8.8.8")      // Force Google DNS
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)       // Capture all internet traffic
                .addDisallowedApplication(packageName) // Prevent the app from tunneling itself

            // CRITICAL: Exclude your VPS IP so the tunnel connection itself stays outside the VPN
            builder.addRoute(vpsIp, 32) 

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                Thread {
                    try {
                        val config = generateFullConfig()
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        coreController?.startLoop(config, fd)
                    } catch (e: Exception) {
                        Log.e("SweetData", "Core Error: ${e.message}")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Crash: ${e.message}")
        }
    }

    private fun generateFullConfig(): String {
        return """
        {
          "log": { "loglevel": "warning" },
          "inbounds": [{
            "port": 10808,
            "protocol": "socks",
            "settings": { "auth": "noauth", "udp": true }
          }],
          "outbounds": [
            {
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
                  "headers": { "Host": "$SNI_BUG" }
                }
              },
              "tag": "proxy"
            },
            { "protocol": "freedom", "tag": "direct" }
          ],
          "routing": {
            "domainStrategy": "AsIs",
            "rules": [{ "type": "field", "outboundTag": "proxy", "port": "0-65535" }]
          }
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
