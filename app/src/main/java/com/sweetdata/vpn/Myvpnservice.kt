package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyManager
import android.util.Log
import libv2ray.Libv2ray 

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        setupAndStartVpn()
        return START_STICKY
    }

    private fun setupAndStartVpn() {
        // --- STEP 1: DETECT CARRIER ---
        val bug = getCarrierBug()
        Log.d("SweetData", "Selected Bug: $bug")

        // --- STEP 2: BUILD VPN INTERFACE ---
        val builder = Builder()
        builder.setSession("SweetData VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDisallowedApplication(packageName) 

        vpnInterface = builder.establish()
        
        if (vpnInterface != null) {
            val config = generateVlessConfig(bug)
            
            Thread {
                try {
                    // Starts the V2Ray core with the chosen bug
                    Libv2ray.runV2Ray(config) 
                } catch (e: Exception) {
                    Log.e("SweetData", "Core Error: ${e.message}")
                }
            }.start()
        }
    }

    private fun getCarrierBug(): String {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()

        return when {
            carrierName.contains("safaricom") -> "v-safaricom.com" // Safaricom Bug
            carrierName.contains("airtel") -> "v.whatsapp.net"    // Airtel Bug
            else -> "www.google.com" // Default fallback
        }
    }

    private fun generateVlessConfig(bug: String): String {
        return """
        {
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{"address": "$vpsIp", "port": 443, "users": [{"id": "$vlessUuid", "encryption": "none"}]}]
            },
            "streamSettings": {
              "network": "ws",
              "security": "tls",
              "tlsSettings": { 
                "serverName": "$bug", 
                "allowInsecure": true 
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
        Libv2ray.stopV2Ray()
        vpnInterface?.close()
        stopSelf()
    }
}
