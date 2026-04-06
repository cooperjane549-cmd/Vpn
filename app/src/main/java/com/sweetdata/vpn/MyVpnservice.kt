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

    // Fix: Added 'override' and fixed capitalization to match library expectations
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        setupAndStartVpn()
        return START_STICKY
    }

    private fun setupAndStartVpn() {
        val bug = getCarrierBug()
        Log.d("SweetData", "Selected Bug: $bug")

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
                    // Fix: Many V2Ray wrappers use 'runV2ray' (lowercase 'r') 
                    // If the error persists, check if it should be Libv2ray.main(config)
                    Libv2ray.runV2ray(config) 
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
            carrierName.contains("safaricom") -> "v-safaricom.com"
            carrierName.contains("airtel") -> "v.whatsapp.net"
            else -> "www.google.com"
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
        try {
            Libv2ray.stopV2ray() 
        } catch (e: Exception) {
            Log.e("SweetData", "Stop Error: ${e.message}")
        }
        vpnInterface?.close()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
