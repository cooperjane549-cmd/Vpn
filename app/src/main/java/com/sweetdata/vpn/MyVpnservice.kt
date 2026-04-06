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
    
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    // --- THE CALLBACK HANDLER ---
    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "Status ($status): $msg")
            return 0
        }

        override fun startup(): Long {
            Log.d("SweetData", "V2Ray Engine Started")
            return 0
        }

        override fun shutdown(): Long {
            Log.d("SweetData", "V2Ray Engine Shutdown")
            return 0
        }
        
        // Removed getAppVersion because your library doesn't require it
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        setupAndStartVpn()
        return START_STICKY
    }

    private fun getBugList(): List<String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()

        return when {
            carrierName.contains("safaricom") -> listOf(
                "api.safaricom.co.ke",
                "v-safaricom.com",
                "safaricom.com",
                "data.education.go.ke"
            )
            carrierName.contains("airtel") -> listOf(
                "v.whatsapp.net",
                "airtellive.com",
                "wynk.in",
                "one.one.one.one"
            )
            else -> listOf("one.one.one.one", "8.8.8.8")
        }
    }

    private fun setupAndStartVpn() {
        val bugs = getBugList()
        
        // Initialize paths
        Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

        val builder = Builder()
        builder.setSession("SweetData VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDisallowedApplication(packageName) 

        vpnInterface = builder.establish()
        
        if (vpnInterface != null) {
            Thread {
                for (activeBug in bugs) {
                    try {
                        Log.d("SweetData", "Trying Bug: $activeBug")
                        val config = generateVlessConfig(activeBug)
                        
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        Libv2ray.touch()
                        
                        // Start the tunnel
                        coreController?.startLoop(config, vpnInterface!!.fd)
                        break 
                    } catch (e: Exception) {
                        Log.e("SweetData", "Bug $activeBug failed, retrying...")
                        continue 
                    }
                }
            }.start()
        }
    }

    private fun generateVlessConfig(bug: String): String {
        return """
        {
          "log": { "loglevel": "warning" },
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "$vpsIp", 
                "port": 443, 
                "users": [{"id": "$vlessUuid", "encryption": "none"}]
              }]
            },
            "streamSettings": {
              "network": "ws",
              "security": "tls",
              "tlsSettings": { "serverName": "$bug", "allowInsecure": true },
              "wsSettings": { "path": "/sweetdata", "headers": { "Host": "$bug" } }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e("SweetData", "Stop Error: ${e.message}")
        }
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
