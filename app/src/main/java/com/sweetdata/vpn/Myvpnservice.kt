package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private var webSocket: WebSocket? = null

    // --- CONFIGURATION ---
    private val vpsIp = "62.169.23.118"
    private val vpsPort = 443
    private val vpsPath = "/sweetdata"
    private val mtuSize = 1400

    // --- DUAL BUG HOSTS ---
    private val SAFARICOM_BUG = "www.safaricom.co.ke"
    private val AIRTEL_BUG = "www.airtel.co.ke"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        if (!running) {
            setupVpn()
        }
        return START_STICKY
    }

    private fun setupVpn() {
        try {
            val builder = Builder()
            builder.setSession("SweetData")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                
                // DETECT THE NETWORK AUTOMATICALLY
                val activeBug = detectNetworkAndGetBug()
                Log.d("SweetData", "Detected network. Using Bug: $activeBug")
                
                startInjectorTunnel(activeBug)
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Failed: ${e.message}")
        }
    }

    private fun detectNetworkAndGetBug(): String {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operatorName = telephonyManager.networkOperatorName.lowercase()

        return when {
            operatorName.contains("safaricom") -> SAFARICOM_BUG
            operatorName.contains("airtel") -> AIRTEL_BUG
            else -> SAFARICOM_BUG // Default to Safaricom if unsure
        }
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug) // INJECT THE CORRECT BUG
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "Connected! Free browsing active on $selectedBug")
                Thread { transferData() }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    val output = FileOutputStream(vpnInterface?.fileDescriptor)
                    output.write(bytes.toByteArray())
                } catch (e: Exception) {
                    Log.e("SweetData", "Download Error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SweetData", "Connection Failed: ${t.message}")
                stopVpn()
            }
        })
    }

    private fun transferData() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(mtuSize)
        try {
            while (running) {
                val length = input.read(buffer)
                if (length > 0) {
                    val data = buffer.copyOfRange(0, length)
                    webSocket?.send(data.toByteString())
                }
            }
        } catch (e: Exception) {
            if (running) Log.e("SweetData", "Upload Error: ${e.message}")
        }
    }

    private fun stopVpn() {
        running = false
        webSocket?.close(1000, "User Stopped")
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
