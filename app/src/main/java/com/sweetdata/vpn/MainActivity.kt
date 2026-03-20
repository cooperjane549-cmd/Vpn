package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure your activity_main.xml has a button with this ID
        val btnStart = Button(this).apply { text = "Start SweetData" }
        setContentView(btnStart)

        btnStart.setOnClickListener {
            startVpn()
        }
    }

    private fun startVpn() {
        // Step 1: Check if Android allows us to start a VPN
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Step 2: If not allowed yet, show the system popup
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            // Step 3: Already allowed, start the service directly
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            // The user clicked "OK" on the system popup
            val intent = Intent(this, MyVpnService::class.java)
            startService(intent)
        }
    }
}
