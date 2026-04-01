package com.sweetdata.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    // --- CONFIGURATION ---
    private val vpsIp = "62.169.23.118"
    private val vpsPort = 443
    private val vpsPath = "/sweetdata"
    private val mtuSize = 1400 
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val SAFARICOM_BUGS = arrayOf("v-safaricom.com", "video.safaricom.et", "www.safaricom.co.ke")
    private val AIRTEL_BUGS = arrayOf("www.airtel.co.ke", "v.whatsapp.net")

    override fun onCreate() {
        super.onCreate()
        // 24/7 UPTIME: Prevent CPU from sleeping
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SweetData:KeepAlive")
    }

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
            if (wakeLock?.isHeld == false) wakeLock?.acquire(10*60*1000L /*10 mins intervals*/)

            val builder = Builder()
            builder.setSession("SweetData Global")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)   // IPv4
                .addRoute("::", 0)        // IPv6 Fix for Safaricom/Global
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(mtuSize)
                .addDisallowedApplication(packageName) // STOP THE LOOP

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
        val selectedBug = getSmartDetectedBug()
        if (selectedBug == "RETRY_EXHAUSTED") {
            currentBugIndex = 0
            return
        }
        startInjectorTunnel(selectedBug)
    }

    private fun getSmartDetectedBug(): String {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrier = telephonyManager.networkOperatorName.lowercase()

        return when {
            carrier.contains("safaricom") -> {
                if (currentBugIndex < SAFARICOM_BUGS.size) SAFARICOM_BUGS[currentBugIndex] else "RETRY_EXHAUSTED"
            }
            carrier.contains("airtel") -> {
                if (currentBugIndex < AIRTEL_BUGS.size) AIRTEL_BUGS[currentBugIndex] else "RETRY_EXHAUSTED"
            }
            else -> "DIRECT" // WORLDWIDE MODE
        }
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder()
            .url("wss://$vpsIp:$vpsPort$vpsPath")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid)

        // Only add Host if not in Direct Worldwide mode
        if (selectedBug != "DIRECT") {
            requestBuilder.header("Host", selectedBug)
        }

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "Connected: $selectedBug")
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
            Log.e("SweetData", "Tunnel Error: ${e.message}")
        }
    }

    private fun isUnlimitedActive(): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        return isForceOn && System.currentTimeMillis() < expiryTime
    }

    private fun updateLocalBalance(bytesSent: Int): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        var balance = prefs.getLong("byte_balance", 0L)
        if (balance <= 0) return false
        val newBalance = balance - bytesSent
        prefs.edit().putLong("byte_balance", newBalance).apply()
        return true
    }

    private fun stopVpn() {
        running = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
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
