package com.asiashield.vpn.domain.model

/** Snapshot of the current VPN session, published by the connection service. */
data class VpnStatus(
    val state: VpnState = VpnState.IDLE,
    val serverId: String? = null,
    /** `SystemClock.elapsedRealtime()` at the moment the tunnel came up. */
    val connectedSinceElapsedMs: Long? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxBytesPerSec: Long = 0,
    val txBytesPerSec: Long = 0,
    val errorMessage: String? = null,
)
