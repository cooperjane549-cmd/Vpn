package com.sweetdata.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "Starting VPN...", Toast.LENGTH_SHORT).show()
        setupVpn()
        return Service.START_STICKY
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

        try {
            while (running) {
                buffer.clear()
                val length = input.read(buffer.array())
                if (length > 0) {
                    output.write(buffer.array(), 0, length) // loopback
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
            output.close()
        }
    }

    override fun onDestroy() {
        running = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        Toast.makeText(this, "VPN Service Stopped", Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }
}
