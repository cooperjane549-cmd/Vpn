package com.sweetdata.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.android.backend.Tunnel

class WireGuardManager(context: Context) {

    private val backend = GoBackend(context)
    private var tunnel: Tunnel? = null

    fun connect() {

        val privateKey = "REPLACE_WITH_CLIENT_PRIVATE_KEY"
        val publicKey = "REPLACE_WITH_SERVER_PUBLIC_KEY"

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
                    .setEndpoint("YOUR_SERVER_IP:51820")
                    .build()
            )
            .build()

        tunnel = object : Tunnel {
            override fun getName() = "sweetdataTunnel"
            override fun onStateChange(newState: Tunnel.State) {}
        }

        backend.setState(tunnel!!, Tunnel.State.UP, config)
    }

    fun disconnect() {
        tunnel?.let {
            backend.setState(it, Tunnel.State.DOWN, null)
        }
    }
}
