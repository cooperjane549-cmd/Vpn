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
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        Toast.makeText(this, "VPN Service Started (ready for future backend)", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MyVpnService::class.java)
        startService(intent)
    }
}
