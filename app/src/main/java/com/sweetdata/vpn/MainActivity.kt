package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sweetdata.vpn.databinding.ActivityMainBinding
import com.wireguard.android.backend.WireGuardManager // Import the correct WireGuardManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vpnManager: WireGuardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vpnManager = WireGuardManager(this)

        binding.btnConnect.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        vpnManager.connect()
    }
}
