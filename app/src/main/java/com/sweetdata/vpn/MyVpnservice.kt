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
    
    // Server Details
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    // --- 1. THE ENGINE HANDLER ---
    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "Status Update ($status): $msg")
            return 0
        }
        override fun startup(): Long {
            Log.i("SweetData", "V2Ray Core is Running")
            return 0
        }
        override fun shutdown(): Long {
            Log.i("SweetData", "V2Ray Core has Stopped")
            return 0
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

    // --- 2. 2026 BUG LIST (The 'Fake IDs') ---
    private fun getBugList(): List<String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()

        return when {
            // Safaricom: Using M-Pesa and payment portals (hardest to block)
            carrierName.contains("safaricom") -> listOf(
                "m-pesaforbusiness.co.ke",
                "daraja.safaricom.co.ke",
                "api.safaricom.co.ke",
                "v-safaricom.com"
            )
            // Airtel: Using FreeBasics and Education portals
            carrierName.contains("airtel") -> listOf(
                "0.freebasics.com",
                "v.whatsapp.net",
                "airtellive.com",
                "selfcare.airtelkenya.com"
            )
            else -> listOf("one.one.one.one", "www.google.com")
        }
    }

    // --- 3. VPN STARTUP ---
    private fun setupAndStartVpn() {
        val bugs = getBugList()
        
        // Initialize paths for the engine
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
                        Log.d("SweetData", "Testing Bug: $activeBug")
                        val config = generateVlessConfig(activeBug)
                        
                        coreController = Libv2ray.newCoreController(v2rayHandler)
                        Libv2ray.touch()
                        
                        // Start the tunnel with the VPN file descriptor
                        coreController?.startLoop(config, vpnInterface!!.fd)
                        
                        // Wait a second to see if it stays connected
                        Thread.sleep(1500)
                        break 
                    } catch (e: Exception) {
                        Log.e("SweetData", "Bug $activeBug failed: ${e.message}")
                        continue 
                    }
                }
            }.start()
        }
    }

    // --- 4. THE POWERFUL CONFIG ---
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
              "tlsSettings": { 
                "serverName": "$bug", 
                "allowInsecure": true,
                "fingerprint": "chrome" 
              },
              "wsSettings": { 
                "path": "/sweetdata", 
                "headers": { "Host": "$bug" } 
              }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e("SweetData", "Stop Error")
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
