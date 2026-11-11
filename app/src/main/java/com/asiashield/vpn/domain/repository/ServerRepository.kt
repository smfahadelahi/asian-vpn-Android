package com.asiashield.vpn.domain.repository

import com.asiashield.vpn.domain.model.VpnServer
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    /** All servers, re-emitted whenever a latency measurement changes. */
    val servers: Flow<List<VpnServer>>

    fun getServer(id: String): VpnServer?

    /** Probes every server and publishes results through [servers] as they arrive. */
    suspend fun refreshLatencies()
}
