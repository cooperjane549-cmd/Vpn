package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sweetdata.vpn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            // Prepare VPN permission
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        // For now, just show a toast
        Toast.makeText(this, "VPN would start here", Toast.LENGTH_SHORT).show()

        // TODO: Launch your MyVpnService here
        val intent = Intent(this, MyVpnService::class.java)
        startService(intent)
    }
}
