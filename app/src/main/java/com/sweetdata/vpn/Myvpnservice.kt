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
    private var currentBugIndex = 0

    // --- YOUR CORE CONFIGURATION (KEEPING THESE) ---
    private val vpsIp = "62.169.23.118"
    private val vpsPort = 443
    private val vpsPath = "/sweetdata"
    private val mtuSize = 1500 
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val SAFARICOM_BUGS = arrayOf(
        "v-safaricom.com", "video.safaricom.et", "static.safaricom.com", "www.safaricom.co.ke"
    )

    private val AIRTEL_BUGS = arrayOf(
        "www.airtel.co.ke", "airtellive.com", "one.airtel.in", "airtelkenya.com"
    )

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
                .addDnsServer("8.8.4.4")
                .addDnsServer("1.1.1.1") 
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                startNextTunnelAttempt()
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Failed: ${e.message}")
        }
    }

    private fun startNextTunnelAttempt() {
        val bugList = getActiveBugList()
        if (currentBugIndex < bugList.size) {
            val selectedBug = bugList[currentBugIndex]
            startInjectorTunnel(selectedBug)
        } else {
            currentBugIndex = 0 // Restart cycling if all fail
        }
    }

    private fun getActiveBugList(): Array<String> {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        // This line now matches the "Safaricom/Airtel" strings sent by MainActivity
        val savedNetwork = prefs.getString("selected_network", "Safaricom")

        return if (savedNetwork == "Airtel") AIRTEL_BUGS else SAFARICOM_BUGS
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        val request = Request.Builder()
            .url("ws://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("User-Agent", userAgent)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid) // Your VLESS String logic
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Thread { transferData() }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    val output = FileOutputStream(vpnInterface?.fileDescriptor)
                    output.write(bytes.toByteArray())
                } catch (e: Exception) { }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running) {
                    currentBugIndex++
                    startNextTunnelAttempt()
                }
            }
        })
    }

    private fun transferData() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(mtuSize)
        
        try {
            while (running) {
                // TIME CHECK: If expiry time passed, kill connection immediately
                if (!hasTimeLeft()) {
                    stopVpn()
                    break
                }

                val length = input.read(buffer)
                if (length > 0) {
                    val data = buffer.copyOfRange(0, length)
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
        return System.currentTimeMillis() < expiryTime
    }

    private fun stopVpn() {
        running = false
        webSocket?.close(1000, "Stopped")
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
