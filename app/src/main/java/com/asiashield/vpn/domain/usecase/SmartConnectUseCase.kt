package com.asiashield.vpn.domain.usecase

import com.asiashield.vpn.domain.model.VpnServer
import javax.inject.Inject

class SmartConnectUseCase @Inject constructor() {
    /** Picks the reachable server with the lowest measured latency, or null if none responded. */
    operator fun invoke(servers: List<VpnServer>): VpnServer? =
        servers.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }
}
