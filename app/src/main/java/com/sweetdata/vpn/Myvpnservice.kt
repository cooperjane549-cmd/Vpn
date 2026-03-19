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
    private var vpnThread: Thread? = null
    private var running = false

    companion object {
        var bytesSent: Long = 0
        var bytesReceived: Long = 0
    }

    // 👉 Replace later with your VPS
    private val remoteHost = "YOUR_VPS_IP"
    private val remotePort = 51820

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VPN", "Starting VPN Service")
        setupVpn()
        return START_STICKY
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("SweetData VPN")
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)

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

        val socket = DatagramSocket()
        socket.soTimeout = 1000

        try {
            while (running) {
                buffer.clear()
                val length = input.read(buffer.array())

                if (length > 0) {
                    // SEND to VPS
                    val packet = DatagramPacket(
                        buffer.array(),
                        length,
                        InetSocketAddress(remoteHost, remotePort)
                    )
                    socket.send(packet)
                    bytesSent += length

                    // RECEIVE from VPS (if available)
                    try {
                        val respBuffer = ByteArray(32767)
                        val response = DatagramPacket(respBuffer, respBuffer.size)
                        socket.receive(response)

                        output.write(response.data, 0, response.length)
                        bytesReceived += response.length

                    } catch (e: Exception) {
                        // No response yet (normal without VPS)
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
            socket.close()
        }
    }

    override fun onDestroy() {
        running = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        Log.d("VPN", "VPN Stopped")
        super.onDestroy()
    }
}
