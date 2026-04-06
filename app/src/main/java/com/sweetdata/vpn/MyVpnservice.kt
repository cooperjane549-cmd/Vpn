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

    // --- 1. THE INTERFACE HANDLER ---
    // Implements all methods required by the libv2ray engine to prevent crashes
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

        override fun getAppVersion(): String {
            return "1.0.0"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        setupAndStartVpn()
        return START_STICKY
    }

    // --- 2. BUG HUNTING LOGIC ---
    private fun getBugList(): List<String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()

        return when {
            carrierName.contains("safaricom") -> listOf(
                "api.safaricom.co.ke",   // M-Pesa/App bypass
                "v-safaricom.com",       // Standard SNI
                "safaricom.com",         // Root domain
                "data.education.go.ke"   // Education bundle
            )
            carrierName.contains("airtel") -> listOf(
                "v.whatsapp.net",        // Social bundle bypass
                "airtellive.com",        // Portal bypass
                "wynk.in",               // Music bypass
                "one.one.one.one"        // Cloudflare fallback
            )
            else -> listOf("one.one.one.one", "8.8.8.8")
        }
    }

    // --- 3. VPN ESTABLISHMENT ---
    private fun setupAndStartVpn() {
        val bugs = getBugList()
        
        // Initialize Core with internal app paths
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
                // Logic: Try each bug in the list until the engine accepts one
                for (activeBug in bugs) {
                    try {
                        Log.d("SweetData", "Hunting with Bug: $activeBug")
                        val config = generateVlessConfig(activeBug)
                        
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        Libv2ray.touch()
                        
                        // Start the tunnel loop
                        coreController?.startLoop(config, vpnInterface!!.fd)
                        
                        // If it starts successfully, we break the loop and stay connected
                        break 
                    } catch (e: Exception) {
                        Log.e("SweetData", "Bug $activeBug failed: ${e.message}")
                        continue // Move to next bug in the list
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
