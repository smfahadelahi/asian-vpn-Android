package com.asiashield.vpn.domain.model

/** Lifecycle of a VPN session. Legal transitions are defined by [canTransitionTo]. */
enum class VpnState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED;

    val isActive: Boolean get() = this == CONNECTING || this == CONNECTED

    fun canTransitionTo(next: VpnState): Boolean = when (this) {
        IDLE -> next == CONNECTING
        CONNECTING -> next == CONNECTED || next == DISCONNECTING || next == FAILED
        CONNECTED -> next == DISCONNECTING || next == FAILED
        DISCONNECTING -> next == IDLE || next == FAILED
        FAILED -> next == CONNECTING || next == IDLE
    }
}
