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
    
    // Server Config
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long = 0
        override fun startup(): Long { Log.i("SweetData", "Core Up"); return 0 }
        override fun shutdown(): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        
        // Only start if we are actually allowed by the system
        if (prepare(this) == null) {
            startVpnEngine()
        } else {
            Log.e("SweetData", "VPN not prepared! Ask user for permission.")
        }
        
        return START_STICKY
    }

    private fun startVpnEngine() {
        try {
            val builder = Builder()
                .setSession("SweetData VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500) // Explicit MTU prevents some hardware crashes
                .addDisallowedApplication(packageName)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                val config = generateConfig("api.safaricom.co.ke")

                Thread {
                    try {
                        // Crucial: Initialize Environment FIRST
                        Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)
                        
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        Libv2ray.touch()
                        
                        // Pass the FD safely
                        coreController?.startLoop(config, fd)
                    } catch (e: Exception) {
                        Log.e("SweetData", "Native Engine Crash: ${e.message}")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Establish failed")
        }
    }

    private fun generateConfig(bug: String): String {
        return """{
          "outbounds": [{
            "protocol": "vless",
            "settings": { "vnext": [{"address": "$vpsIp", "port": 443, "users": [{"id": "$vlessUuid"}]}] },
            "streamSettings": { "network": "ws", "security": "tls", 
            "tlsSettings": { "serverName": "$bug", "allowInsecure": true },
            "wsSettings": { "path": "/sweetdata", "headers": { "Host": "$bug" } } }
          }]
        }""".trimIndent()
    }

    private fun stopVpn() {
        coreController?.stopLoop()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }
}
