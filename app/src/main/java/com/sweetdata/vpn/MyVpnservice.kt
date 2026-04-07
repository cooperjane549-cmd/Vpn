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
    
    // Server Config - MUST match your VPS /usr/local/etc/xray/config.json
    private val vpsIp = "62.169.23.118"
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "Status: $msg")
            return 0
        }
        override fun startup(): Long {
            isRunning = true
            Log.i("SweetData", "V2Ray Engine Started Successfully")
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
        if (!isRunning) setupAndStartVpn()
        return START_STICKY
    }

    private fun getBugList(): List<String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = tm.networkOperatorName.lowercase()
        return when {
            // Safaricom 2026 working SNIs
            carrierName.contains("safaricom") -> listOf(
                "m-pesaforbusiness.co.ke", 
                "api.safaricom.co.ke",
                "daraja.safaricom.co.ke"
            )
            // Airtel 2026 working SNIs
            carrierName.contains("airtel") -> listOf(
                "v.whatsapp.net", 
                "0.freebasics.com",
                "selfcare.airtelkenya.com"
            )
            else -> listOf("one.one.one.one")
        }
    }

    private fun setupAndStartVpn() {
        val bugs = getBugList()
        
        // Initialize the Core Environment
        Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)

        val builder = Builder()
            .setSession("SweetData VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0) 
            .addDnsServer("1.1.1.1")
            .setMtu(1400) // Optimal for Safaricom/Airtel LTE
            .addDisallowedApplication(packageName) 

        // CRITICAL: Exclude VPS IP from the tunnel to prevent connection loops
        try {
            builder.addRoute(vpsIp, 32)
        } catch (e: Exception) {
            Log.e("SweetData", "Route exclusion failed: ${e.message}")
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                
                Thread {
                    // Give the system a moment to stabilize the TUN interface
                    Thread.sleep(1000) 
                    
                    for (activeBug in bugs) {
                        if (!isRunning && vpnInterface != null) {
                            try {
                                Log.d("SweetData", "Attempting connection with bug: $activeBug")
                                val config = generateVlessConfig(activeBug)
                                
                                coreController = Libv2ray.newCoreController(v2rayHandler)
                                Libv2ray.touch()
                                
                                // Launch the V2Ray core loop
                                coreController?.startLoop(config, fd)
                                break 
                            } catch (e: Exception) {
                                Log.e("SweetData", "Bug $activeBug failed, trying next...")
                                continue 
                            }
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Interface establishment failed")
            stopSelf()
        }
    }

    private fun generateVlessConfig(bug: String): String {
        // MATCHES VPS: security: none, network: ws, path: /sweetdata
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
              "security": "none", 
              "wsSettings": { 
                "path": "/sweetdata", 
                "headers": { 
                  "Host": "$bug",
                  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"
                } 
              }
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
