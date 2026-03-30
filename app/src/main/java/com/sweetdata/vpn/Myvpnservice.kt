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
    private val mtuSize = 1500 
    
    // Your VLESS UUID for Authentication
    private val vlessUuid = "25bd8cc6-90eb-4a94-9bd1-051ae1c98a0b"

    private val SAFARICOM_BUG = "www.safaricom.co.ke"
    private val AIRTEL_BUG = "www.airtel.co.ke"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("SweetDataPrefs", Context.MODE_PRIVATE)
        val isForceOn = prefs.getBoolean("force_vpn", false)
        val expiryTime = prefs.getLong("expiry_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (intent?.action == "STOP") {
            if (isForceOn && currentTime < expiryTime) {
                Log.d("SweetData", "STOP Ignored: 3hr Reward Active")
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
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://$vpsIp:$vpsPort$vpsPath")
            .header("Host", selectedBug)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            // VLESS Auth Header
            .header("Sec-WebSocket-Protocol", vlessUuid)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SweetData", "VLESS Active via $selectedBug")
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
                Log.e("SweetData", "Tunnel Failure: ${t.message}")
                
                // Auto-reconnect if 3-hour unlimited is active
                if (isUnlimitedActive() && running) {
                    Log.d("SweetData", "Reconnecting in 5s...")
                    Thread.sleep(5000)
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
