package com.asiashield.vpn

import com.asiashield.vpn.data.repository.AsiaServerRepository
import com.asiashield.vpn.domain.model.AsiaRegion
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.domain.model.flagEmojiOf
import com.asiashield.vpn.domain.usecase.FilterServersUseCase
import com.asiashield.vpn.domain.usecase.SmartConnectUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private val catalog = AsiaServerRepository.CATALOG

    @Test
    fun catalogCoversEveryRegionWithUniqueIds() {
        assertEquals(AsiaRegion.entries.toSet(), catalog.map { it.region }.toSet())
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        assertEquals(catalog.size, catalog.map { it.publicKey }.toSet().size)
    }

    @Test
    fun stateMachineRejectsIllegalTransitions() {
        assertTrue(VpnState.IDLE.canTransitionTo(VpnState.CONNECTING))
        assertTrue(VpnState.CONNECTING.canTransitionTo(VpnState.CONNECTED))
        assertTrue(VpnState.CONNECTED.canTransitionTo(VpnState.DISCONNECTING))
        assertTrue(VpnState.DISCONNECTING.canTransitionTo(VpnState.IDLE))
        assertFalse(VpnState.IDLE.canTransitionTo(VpnState.CONNECTED))
        assertFalse(VpnState.CONNECTED.canTransitionTo(VpnState.CONNECTING))
        assertFalse(VpnState.DISCONNECTING.canTransitionTo(VpnState.CONNECTED))
    }

    @Test
    fun filterMatchesRegionAndQuery() {
        val filter = FilterServersUseCase()
        assertEquals(catalog, filter(catalog, null, ""))
        assertTrue(filter(catalog, AsiaRegion.CENTRAL_ASIA, "").all { it.region == AsiaRegion.CENTRAL_ASIA })
        assertEquals(listOf("jp-tyo"), filter(catalog, null, " tokyo ").map { it.id })
        assertTrue(filter(catalog, AsiaRegion.MIDDLE_EAST, "tokyo").isEmpty())
    }

    @Test
    fun smartConnectPicksLowestLatencyAndIgnoresUnreachable() {
        val smart = SmartConnectUseCase()
        assertNull(smart(catalog))
        val measured = catalog.mapIndexed { i, s ->
            when (i) {
                0 -> s.copy(latencyMs = null, latencyChecked = true)
                3 -> s.copy(latencyMs = 20, latencyChecked = true)
                else -> s.copy(latencyMs = 100 + i, latencyChecked = true)
            }
        }
        assertEquals(catalog[3].id, smart(measured)?.id)
    }

    @Test
    fun flagEmojiUsesRegionalIndicators() {
        assertEquals("\uD83C\uDDEF\uD83C\uDDF5", flagEmojiOf("JP"))
    }
}
