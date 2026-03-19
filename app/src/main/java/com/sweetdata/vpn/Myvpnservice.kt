package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var running = false

    // Replace with your VPS IP/Port
    private val remoteHost = "YOUR_VPS_IP"
    private val remotePort = 51820 // UDP port typical for VPN

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyVpnService", "Starting VPN Service")
        setupVpn()
        return START_STICKY
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("SweetData VPN")
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0) // route all traffic through VPN
        vpnInterface = builder.establish()

        vpnInterface?.let { fd ->
            running = true
            vpnThread = Thread { runVpn(fd.fileDescriptor) }
            vpnThread?.start()
        }
    }

    private fun runVpn(fd: java.io.FileDescriptor) {
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(32767)

        // Setup UDP socket to forward traffic to VPS
        val udpSocket = DatagramSocket()
        udpSocket.soTimeout = 1000

        try {
            while (running) {
                buffer.clear()
                val length = input.read(buffer.array())
                if (length > 0) {
                    // Forward to VPS
                    val packetData = buffer.array().copyOf(length)
                    val packet = java.net.DatagramPacket(packetData, length, InetSocketAddress(remoteHost, remotePort))
                    udpSocket.send(packet)

                    // Optional: read response from VPS (if you implement return path)
                    try {
                        val responseBuffer = ByteArray(32767)
                        val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
                        udpSocket.receive(responsePacket)
                        output.write(responsePacket.data, 0, responsePacket.length)
                    } catch (e: SocketException) {
                        // timeout is expected if VPS doesn’t reply immediately
                    }
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
            output.close()
            udpSocket.close()
        }
    }

    override fun onDestroy() {
        running = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        Log.d("MyVpnService", "VPN Service Stopped")
        super.onDestroy()
    }
}
