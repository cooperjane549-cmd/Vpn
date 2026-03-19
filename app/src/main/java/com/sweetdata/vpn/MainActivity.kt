package com.sweetdata.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.sweetdata.vpn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

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

        startMonitoring()
    }

    private fun startVpn() {
        val intent = Intent(this, MyVpnService::class.java)
        startService(intent)
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                binding.txtUpload.text = "Upload: ${MyVpnService.bytesSent} B"
                binding.txtDownload.text = "Download: ${MyVpnService.bytesReceived} B"
                handler.postDelayed(this, 1000)
            }
        })
    }
}
