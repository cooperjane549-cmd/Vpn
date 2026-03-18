package com.sweetdata.vpn

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.TunnelManager
import com.wireguard.android.backend.TunnelManagerBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

class WireGuardManager(context: Context) {

    private val tunnelManager: TunnelManagerBackend = TunnelManager.getBackend(context)
    private var tunnel: Tunnel? = null

    fun connect() {
        try {
            // Replace these with your server/client keys
            val privateKey = "REPLACE_WITH_CLIENT_PRIVATE_KEY"
            val publicKey = "REPLACE_WITH_SERVER_PUBLIC_KEY"
            val endpoint = "YOUR_SERVER_IP:51820"

            val config = Config.Builder()
                .setInterface(
                    Interface.Builder()
                        .addAddress("10.0.0.2/32")
                        .setPrivateKey(privateKey)
                        .build()
                )
                .addPeer(
                    Peer.Builder()
                        .setPublicKey(publicKey)
                        .addAllowedIp("0.0.0.0/0")
                        .setEndpoint(endpoint)
                        .build()
                )
                .build()

            tunnel = object : Tunnel {
                override fun getName() = "sweetdataTunnel"
                override fun onStateChange(newState: Tunnel.State) {
                    Log.d("WireGuardManager", "Tunnel state: $newState")
                }
            }

            tunnelManager.setState(tunnel!!, Tunnel.State.UP, config)
            Log.d("WireGuardManager", "VPN connected successfully!")

        } catch (e: Exception) {
            Log.e("WireGuardManager", "Failed to connect VPN", e)
        }
    }

    fun disconnect() {
        tunnel?.let {
            tunnelManager.setState(it, Tunnel.State.DOWN, null)
            Log.d("WireGuardManager", "VPN disconnected")
        }
    }
}
