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

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    // Configuration
    private val remoteHost = "62.169.23.118" // Your Contabo IP
    private val remotePort = 51820
    private val mtuSize = 1400 // Slightly higher for better speed on Contabo

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
                .addDnsServer("1.1.1.1") 
                .setMtu(mtuSize)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                running = true
                // Pass the FileDescriptor to the tunnel logic
                val fd = vpnInterface!!.fileDescriptor
                Thread { startTunnel(fd) }.start()
                Log.d("SweetData", "VPN Tunnel Established to $remoteHost")
            }
        } catch (e: Exception) {
            Log.e("SweetData", "Setup Failed: ${e.message}")
        }
    }

    private fun startTunnel(fd: java.io.FileDescriptor) {
        val socket = DatagramSocket()
        
        try {
            protect(socket) // Prevents infinite loop
            socket.connect(InetSocketAddress(remoteHost, remotePort))
            
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)

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
                } catch (e: Exception) {
                    if (running) Log.e("SweetData", "Upload Error: ${e.message}")
                }
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
                } catch (e: Exception) {
                    if (running) Log.e("SweetData", "Download Error: ${e.message}")
                }
            }

            uploadThread.start()
            downloadThread.start()

        } catch (e: Exception) {
            Log.e("SweetData", "Socket Error: ${e.message}")
        }
    }

    private fun stopVpn() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
        Log.d("SweetData", "VPN Stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
