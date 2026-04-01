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

    // --- CONFIGURATION ---
    private val vpsIp = "62.169.23.118"
    private val vpsPort = 443
    private val vpsPath = "/sweetdata"
    private val mtuSize = 1400 
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    // Expanded Bug Lists
    private val SAFARICOM_BUGS = arrayOf(
        "www.safaricom.co.ke",
        "video.safaricom.et",
        "static.safaricom.com",
        "v-safaricom.com"
    )
    private val AIRTEL_BUGS = arrayOf(
        "www.airtel.co.ke",
        "one.airtel.in",
        "v.whatsapp.net"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (intent?.action == "STOP") {
            if (isForceOn && currentTime < expiryTime) return START_STICKY 
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
                .addRoute("0.0.0.0", 0)   // IPv4
                .addRoute("::", 0)        // IPv6 (Crucial for Safaricom)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(mtuSize)
                // STOP THE LOOP: Exclude this app from its own tunnel
                .addDisallowedApplication(packageName)

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
            currentBugIndex = 0 // Reset and retry
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

        val request = Request.Builder()
            .url("wss://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "Connected via $selectedBug")
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
                    Log.e("SweetData", "Bug $selectedBug failed. Trying next...")
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
                val length = input.read(buffer)
                if (length > 0) {
                    if (!isUnlimitedActive()) {
                        if (!updateLocalBalance(length)) {
                            stopVpn()
                            break
                        }
                    }
                    val data = buffer.copyOfRange(0, length)
                    webSocket?.send(data.toByteString())
                }
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Upload Error: ${e.message}")
        }
    }

    private fun isUnlimitedActive(): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        
        if (isForceOn && System.currentTimeMillis() > expiryTime) {
            prefs.edit().putBoolean("force_vpn", false).apply()
            return false
        }
        return isForceOn
    }

    private fun updateLocalBalance(bytesSent: Int): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        var balance = prefs.getLong("byte_balance", 0L)
        if (balance <= 0L) {
            balance = prefs.getInt("mb_balance", 0).toLong() * 1024 * 1024
        }
        if (balance <= 0) return false
        val newBalance = balance - bytesSent
        prefs.edit().putLong("byte_balance", newBalance)
            .putInt("mb_balance", (newBalance / (1024 * 1024)).toInt())
            .apply()
        return true
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
