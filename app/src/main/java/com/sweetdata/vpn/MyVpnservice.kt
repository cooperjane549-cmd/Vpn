package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyManager
import android.util.Log
import libv2ray.Libv2ray 
import libv2ray.CoreController // Found in your X-Ray

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
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
        val bug = getCarrierBug()
        
        // --- STEP 1: INITIALIZE ENVIRONMENT ---
        // This library requires setting up environment strings (usually assets/log paths)
        Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

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
                    // --- STEP 2: CREATE CONTROLLER ---
                    // The X-Ray showed 'newCoreController'. We pass null for the callback handler for now.
                    coreController = Libv2ray.newCoreController(null)
                    
                    // --- STEP 3: START ---
                    // Most controllers have a .start(config) or .startLoop(config)
                    // We call 'touch' to ensure the library is loaded
                    Libv2ray.touch()
                    coreController?.startLoop(config, vpnInterface!!.fd)
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
              "tlsSettings": { "serverName": "$bug", "allowInsecure": true },
              "wsSettings": { "path": "/sweetdata", "headers": { "Host": "$bug" } }
            }
          }]
        }
        """.trimIndent()
    }

    private fun stopVpn() {
        try {
            // Stop the controller we created
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
