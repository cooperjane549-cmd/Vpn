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
    private val mtuSize = 1400 // FIX 1: Lowered from 1500 to prevent packet dropping
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val SAFARICOM_BUG = "www.safaricom.co.ke"
    private val AIRTEL_BUG = "www.airtel.co.ke"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (intent?.action == "STOP") {
            // Only stop if the unlimited timer hasn't expired
            if (isForceOn && currentTime < expiryTime) {
                return START_STICKY 
            }
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
            builder.setSession("SweetDataVPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                val activeBug = detectNetworkAndGetBug()
                startInjectorTunnel(activeBug)
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Failed: ${e.message}")
        }
    }

    private fun detectNetworkAndGetBug(): String {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operatorName = telephonyManager.networkOperatorName.lowercase()
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val savedNetwork = prefs.getString("selected_network", null)

        return when {
            savedNetwork == "Safaricom" -> SAFARICOM_BUG
            savedNetwork == "Airtel" -> AIRTEL_BUG
            operatorName.contains("airtel") -> AIRTEL_BUG
            else -> SAFARICOM_BUG
        }
    }

    private fun startInjectorTunnel(selectedBug: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        // FIX 2: Using wss:// for port 443
        val request = Request.Builder()
            .url("wss://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Protocol", vlessUuid)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Thread { transferData() }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    // Open the output stream once and write
                    val output = FileOutputStream(vpnInterface?.fileDescriptor)
                    output.write(bytes.toByteArray())
                } catch (e: Exception) { }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isUnlimitedActive() && running) {
                    // Auto-reconnect logic
                    Thread.sleep(3000)
                    startInjectorTunnel(selectedBug)
                } else {
                    stopVpn()
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
                    // FIX 3: Ensure we don't stop if the user has NO balance but HAS unlimited time
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
        
        // If time is up, reset the force_vpn flag
        if (isForceOn && System.currentTimeMillis() > expiryTime) {
            prefs.edit().putBoolean("force_vpn", false).apply()
            return false
        }
        return isForceOn
    }

    private fun updateLocalBalance(bytesSent: Int): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        var balance = prefs.getLong("byte_balance", 0L)
        
        // Convert MB to bytes if byte_balance is missing
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
