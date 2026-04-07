package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyManager
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

    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "Status: $msg")
            return 0
        }
        override fun startup(): Long {
            isRunning = true
            Log.i("SweetData", "Engine Started Successfully")
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
        
        // Prevent double-starting which causes crashes
        if (!isRunning) {
            setupAndStartVpn()
        }
        return START_STICKY
    }

    private fun getBugList(): List<String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()

        return when {
            carrierName.contains("safaricom") -> listOf("m-pesaforbusiness.co.ke", "api.safaricom.co.ke")
            carrierName.contains("airtel") -> listOf("v.whatsapp.net", "0.freebasics.com")
            else -> listOf("one.one.one.one")
        }
    }

    private fun setupAndStartVpn() {
        val bugs = getBugList()
        Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

        val builder = Builder()
        builder.setSession("SweetData VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0) // This sends ALL traffic to the VPN
            .addDnsServer("8.8.8.8")
            .addDisallowedApplication(packageName) 

        try {
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                Thread {
                    // CRITICAL FIX: Give Android 500ms to stabilize the interface
                    Thread.sleep(500) 
                    
                    for (activeBug in bugs) {
                        try {
                            if (vpnInterface == null) break
                            
                            val config = generateVlessConfig(activeBug)
                            coreController = Libv2ray.newCoreController(v2rayHandler)
                            Libv2ray.touch()
                            
                            // Start the engine
                            coreController?.startLoop(config, vpnInterface!!.fd)
                            break 
                        } catch (e: Exception) {
                            Log.e("SweetData", "Bug failed: ${e.message}")
                            continue 
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Establish failed: ${e.message}")
            stopSelf()
        }
    }

    private fun generateVlessConfig(bug: String): String {
        return """
        {
          "log": { "loglevel": "warning" },
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{"address": "$vpsIp", "port": 443, "users": [{"id": "$vlessUuid", "encryption": "none"}]}]
            },
            "streamSettings": {
              "network": "ws",
              "security": "tls",
              "tlsSettings": { "serverName": "$bug", "allowInsecure": true, "fingerprint": "chrome" },
              "wsSettings": { "path": "/sweetdata", "headers": { "Host": "$bug" } }
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
