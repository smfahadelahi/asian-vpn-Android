package com.asiashield.vpn.data.vpn

import com.wireguard.android.backend.Tunnel

class WireGuardTunnel(
    private val tunnelName: String,
    private val onStateChanged: (Tunnel.State) -> Unit,
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) = onStateChanged(newState)
}
