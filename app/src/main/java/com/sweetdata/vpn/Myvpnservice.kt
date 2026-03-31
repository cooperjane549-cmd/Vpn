package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private var webSocket: WebSocket? = null
    private var currentBugIndex = 0

    // Streams kept open to prevent overhead/crashes
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    // --- CONFIGURATION ---
    private val vpsIp = "62.169.23.118"
    private val vpsPort = 443
    private val vpsPath = "/sweetdata"
    
    // REDUCED MTU: 1500 is often too large for tunneled packets (headers add size)
    private val mtuSize = 1400 
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val SAFARICOM_BUGS = arrayOf("v-safaricom.com", "video.safaricom.et", "static.safaricom.com", "www.safaricom.co.ke")
    private val AIRTEL_BUGS = arrayOf("www.airtel.co.ke", "airtellive.com", "one.airtel.in", "airtelkenya.com", "v.whatsapp.net")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!running) {
            currentBugIndex = 0
            setupVpn()
        }
        return START_STICKY
    }

    private fun setupVpn() {
        try {
            val builder = Builder()
            builder.setSession("SweetDataVPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                // Added Google and Cloudflare DNS for better compatibility
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1") 
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                // Initialize streams once
                inputStream = FileInputStream(vpnInterface?.fileDescriptor)
                outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                
                startNextTunnelAttempt()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "VPN Setup Failed: ${e.message}")
        }
    }

    private fun startNextTunnelAttempt() {
        val bugList = getActiveBugList()
        if (currentBugIndex < bugList.size) {
            startInjectorTunnel(bugList[currentBugIndex])
        } else {
            currentBugIndex = 0 // Cycle back
            Log.d("SweetData", "All bugs failed, retrying...")
        }
    }

    private fun getActiveBugList(): Array<String> {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val savedNetwork = prefs.getString("selected_network", "Safaricom")
        return if (savedNetwork == "Airtel") AIRTEL_BUGS else SAFARICOM_BUGS
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        // Using WSS (Secure) for Port 443
        val request = Request.Builder()
            .url("wss://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "Tunnel Connected via $selectedBug")
                Thread { readFromVpn() }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    // Write back to the phone's network stack
                    outputStream?.write(bytes.toByteArray())
                } catch (e: Exception) {
                    Log.e("SweetData", "Downlink Error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running) {
                    Log.d("SweetData", "Bug $selectedBug failed, trying next...")
                    currentBugIndex++
                    startNextTunnelAttempt()
                }
            }
        })
    }

    private fun readFromVpn() {
        val buffer = ByteArray(mtuSize)
        try {
            while (running) {
                // EXPIRE CHECK: If time is up, kill the loop
                if (!hasTimeLeft()) {
                    Log.d("SweetData", "Time expired. Stopping.")
                    stopVpn()
                    break
                }

                val length = inputStream?.read(buffer) ?: -1
                if (length > 0) {
                    val data = buffer.copyOfRange(0, length)
                    // Send out to the VPS
                    webSocket?.send(data.toByteString())
                }
            }
        } catch (e: Exception) {
            if (running) stopVpn()
        }
    }

    private fun hasTimeLeft(): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        // If expiry is 0, we assume the user hasn't earned time yet
        return System.currentTimeMillis() < expiryTime
    }

    private fun stopVpn() {
        running = false
        webSocket?.close(1000, "User Stopped")
        try {
            vpnInterface?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (e: IOException) { }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
