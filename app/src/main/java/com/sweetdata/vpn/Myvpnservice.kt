package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    // Configuration - Change these to your VPS details
    private val remoteHost = "100.64.201.65" // Put your VPS IP here
    private val remotePort = 51820
    private val mtuSize = 1280 // Standard for VPN compatibility

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                .addRoute("0.0.0.0", 0) // Route ALL traffic
                .addDnsServer("8.8.8.8") // Google DNS
                .addDnsServer("1.1.1.1") // Cloudflare DNS
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                // Start the core logic in a background thread
                Thread { startTunnel(vpnInterface!!.fileDescriptor) }.start()
                Log.d("SweetData", "VPN Tunnel Established")
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Failed: ${e.message}")
        }
    }

    private fun startTunnel(fd: java.io.FileDescriptor) {
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        
        // Create the socket and PROTECT it
        val socket = DatagramSocket()
        protect(socket) // This is the MOST important line
        socket.connect(InetSocketAddress(remoteHost, remotePort))

        // Thread 1: Phone -> VPS (Upload)
        val uploadThread = Thread {
            val buffer = ByteArray(mtuSize)
            try {
                while (running) {
                    val length = input.read(buffer)
                    if (length > 0) {
                        val packet = DatagramPacket(buffer, length)
                        socket.send(packet)
                    }
                }
            } catch (e: Exception) { Log.e("SweetData", "Upload Error: ${e.message}") }
        }

        // Thread 2: VPS -> Phone (Download)
        val downloadThread = Thread {
            val buffer = ByteArray(mtuSize + 100)
            try {
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    output.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) { Log.e("SweetData", "Download Error: ${e.message}") }
        }

        uploadThread.start()
        downloadThread.start()
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        Log.d("SweetData", "VPN Stopped")
        super.onDestroy()
    }
}
