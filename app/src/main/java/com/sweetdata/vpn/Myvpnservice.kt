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
    private val mtuSize = 1500 
    
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    // MULTI-BUG LISTS (Ranked by stability)
    private val SAFARICOM_BUGS = arrayOf(
        "v-safaricom.com",
        "video.safaricom.et",
        "static.safaricom.com",
        "www.safaricom.co.ke",
        "m.safaricom.co.ke"
    )

    private val AIRTEL_BUGS = arrayOf(
        "www.airtel.co.ke",
        "airtellive.com",
        "one.airtel.in",
        "airtelkenya.com"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (intent?.action == "STOP") {
            if (isForceOn && currentTime < expiryTime) {
                return START_STICKY 
            }
            stopVpn()
            return START_NOT_STICKY
        }

        if (!running) {
            currentBugIndex = 0 // Reset index on new start
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
                // Using 8.8.4.4 as primary often works better on Safaricom
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
            Log.d("SweetData", "Attempting Connection via: $selectedBug")
            startInjectorTunnel(selectedBug)
        } else {
            Log.e("SweetData", "All bugs failed. Checking connection...")
            currentBugIndex = 0 // Reset for next retry
        }
    }

    private fun getActiveBugList(): Array<String> {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operatorName = telephonyManager.networkOperatorName.lowercase()
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val savedNetwork = prefs.getString("selected_network", null)

        return when {
            savedNetwork == "Safaricom" -> SAFARICOM_BUGS
            savedNetwork == "Airtel" -> AIRTEL_BUGS
            operatorName.contains("airtel") -> AIRTEL_BUGS
            else -> SAFARICOM_BUGS
        }
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // Shorter timeout to cycle bugs faster
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        // Spoof User-Agent to look like Chrome on Windows (Safaricom bypass)
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        val request = Request.Builder()
            .url("ws://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("User-Agent", userAgent)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "CONNECTED: $selectedBug is Working!")
                Thread { transferData() }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    val output = FileOutputStream(vpnInterface?.fileDescriptor)
                    output.write(bytes.toByteArray())
                } catch (e: Exception) {
                    Log.e("SweetData", "Stream Error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SweetData", "Bug $selectedBug Failed: ${t.message}")
                
                if (running) {
                    currentBugIndex++ // Move to the next bug in the list
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
            if (running) Log.e("SweetData", "Upload Error: ${e.message}")
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
        var balance = prefs.getLong("byte_balance", -1L)
        
        if (balance == -1L) {
            balance = prefs.getInt("mb_balance", 0).toLong() * 1024 * 1024
        }

        balance -= bytesSent

        if (balance <= 0) {
            prefs.edit().putLong("byte_balance", 0).putInt("mb_balance", 0).apply()
            return false
        }

        prefs.edit().putLong("byte_balance", balance).apply()
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
