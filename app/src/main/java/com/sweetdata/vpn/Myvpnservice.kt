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
            builder.setSession("SweetDataVPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
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

        // Check user preference first, fallback to auto-detect
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
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
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
                    // Optional: You can also subtract balance on Download, but 
                    // most injectors only track Upload to save CPU/Battery.
                } catch (e: Exception) {
                    Log.e("SweetData", "Download Error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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
                    
                    // --- DATA TRACKING & DRAIN LOGIC ---
                    if (!updateLocalBalance(length)) {
                        Log.d("SweetData", "Balance empty. Killing tunnel.")
                        stopVpn()
                        break
                    }
                    
                    val data = buffer.copyOfRange(0, length)
                    webSocket?.send(data.toByteString())
                }
            }
        } catch (e: Exception) {
            if (running) Log.e("SweetData", "Upload Error: ${e.message}")
        }
    }

    /**
     * Updates the MB balance in SharedPreferences.
     * Returns true if balance is > 0, false if empty.
     */
    private fun updateLocalBalance(bytesSent: Int): Boolean {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val currentBalanceInBytes = prefs.getLong("byte_balance", -1L)
        
        // If it's the first time, convert MB to Bytes for accuracy
        var balance = if (currentBalanceInBytes == -1L) {
            prefs.getInt("mb_balance", 0).toLong() * 1024 * 1024
        } else {
            currentBalanceInBytes
        }

        balance -= bytesSent

        if (balance <= 0) {
            prefs.edit().putLong("byte_balance", 0).putInt("mb_balance", 0).apply()
            return false
        }

        // Save the high-precision byte balance
        prefs.edit().putLong("byte_balance", balance).apply()
        
        // Update the MB balance for the UI display (every 1MB)
        val mbDisplay = (balance / (1024 * 1024)).toInt()
        prefs.edit().putInt("mb_balance", mbDisplay).apply()
        
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
