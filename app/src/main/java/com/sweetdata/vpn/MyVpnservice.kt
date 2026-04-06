package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyManager
import android.util.Log
import libv2ray.Libv2ray 
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler // Add this import

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    
    // Create a simple handler to prevent the 'null' crash
    private val v2rayHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d("SweetData", "V2Ray Status ($status): $msg")
            return 0
        }
        // If your X-Ray showed other methods in CoreCallbackHandler, add them here
    }

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
        
        // Initialize the Core Environment
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
                    // FIX: Pass the 'v2rayHandler' instead of 'null'
                    coreController = Libv2ray.newCoreController(v2rayHandler)
                    
                    Libv2ray.touch()
                    
                    // Note: Use the exact method name found in your X-Ray for CoreController
                    coreController?.startLoop(config, vpnInterface!!.fd)
                } catch (e: Exception) {
                    Log.e("SweetData", "Core Error: ${e.message}")
                }
            }.start()
        }
    }

    // ... (Keep your getCarrierBug and generateVlessConfig functions the same)

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
