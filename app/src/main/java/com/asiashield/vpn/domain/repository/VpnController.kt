package com.asiashield.vpn.domain.repository

import android.content.Intent
import com.asiashield.vpn.domain.model.VpnStatus
import kotlinx.coroutines.flow.StateFlow

/** Entry point the presentation layer uses to drive the VPN session. */
interface VpnController {
    val status: StateFlow<VpnStatus>

    /**
     * Returns the system consent intent that must be launched before a tunnel can be
     * established, or null when the user has already granted VPN permission.
     */
    fun prepareIntent(): Intent?

    fun connect(serverId: String)

    fun disconnect()
}
