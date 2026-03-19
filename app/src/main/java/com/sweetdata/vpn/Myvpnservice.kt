package com.sweetdata.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        setupVpn()
        return START_STICKY
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.setSession("SweetData VPN")
        vpnInterface = builder.establish()

        vpnInterface?.let { fd ->
            Thread {
                runVpn(fd.fileDescriptor)
            }.start()
        }
    }

    private fun runVpn(fd: java.io.FileDescriptor) {
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(32767)

        while (true) {
            buffer.clear()
            val length = input.read(buffer.array())
            if (length > 0) {
                // This is where you can route traffic to your VPS
                output.write(buffer.array(), 0, length)
            }
        }
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }
}
