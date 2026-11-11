package com.asiashield.vpn.presentation

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asiashield.vpn.domain.model.AsiaRegion
import com.asiashield.vpn.domain.model.VpnServer
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.domain.model.VpnStatus
import com.asiashield.vpn.domain.repository.ServerRepository
import com.asiashield.vpn.domain.repository.SettingsRepository
import com.asiashield.vpn.domain.repository.VpnController
import com.asiashield.vpn.domain.usecase.FilterServersUseCase
import com.asiashield.vpn.domain.usecase.SmartConnectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VpnUiState(
    val status: VpnStatus = VpnStatus(),
    val servers: List<VpnServer> = emptyList(),
    val filteredServers: List<VpnServer> = emptyList(),
    val selectedServer: VpnServer? = null,
    /** null means "All". */
    val regionFilter: AsiaRegion? = null,
    val query: String = "",
    val sessionSeconds: Long = 0,
    val isMeasuringLatency: Boolean = false,
) {
    val state: VpnState get() = status.state
}

sealed interface VpnEvent {
    /** The system VPN consent dialog must be shown before connecting. */
    data class RequestVpnPermission(val intent: Intent) : VpnEvent

    data class Message(val text: String) : VpnEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VpnViewModel @Inject constructor(
    private val controller: VpnController,
    private val serverRepository: ServerRepository,
    private val settings: SettingsRepository,
    private val filterServers: FilterServersUseCase,
    private val smartConnect: SmartConnectUseCase,
) : ViewModel() {

    private data class Filters(
        val region: AsiaRegion? = null,
        val query: String = "",
        val measuring: Boolean = false,
    )

    private val filters = MutableStateFlow(Filters())

    private val _events = Channel<VpnEvent>(Channel.BUFFERED)
    val events: Flow<VpnEvent> = _events.receiveAsFlow()

    /** Server waiting for the user to answer the VPN consent dialog. */
    private var pendingServerId: String? = null

    /** Ticks once a second while a session is up. */
    private val sessionSeconds: Flow<Long> = controller.status
        .map { it.connectedSinceElapsedMs }
        .distinctUntilChanged()
        .flatMapLatest { since ->
            if (since == null) {
                flowOf(0L)
            } else {
                flow {
                    while (true) {
                        emit((SystemClock.elapsedRealtime() - since) / 1000)
                        delay(1000)
                    }
                }
            }
        }

    val uiState: StateFlow<VpnUiState> = combine(
        controller.status,
        serverRepository.servers,
        settings.selectedServerId,
        filters,
        sessionSeconds,
    ) { status, servers, selectedId, f, seconds ->
        VpnUiState(
            status = status,
            servers = servers,
            filteredServers = filterServers(servers, f.region, f.query),
            selectedServer = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull(),
            regionFilter = f.region,
            query = f.query,
            sessionSeconds = seconds,
            isMeasuringLatency = f.measuring,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnUiState())

    init {
        // Keep the ping indicators fresh while the UI is alive.
        viewModelScope.launch {
            while (true) {
                measureLatencies()
                delay(LATENCY_REFRESH_MS)
            }
        }
    }

    // ------------------------------------------------------------------ filtering

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    fun onRegionSelected(region: AsiaRegion?) = filters.update { it.copy(region = region) }

    fun refreshLatencies() {
        viewModelScope.launch { measureLatencies() }
    }

    private suspend fun measureLatencies() {
        if (filters.value.measuring) return
        filters.update { it.copy(measuring = true) }
        try {
            serverRepository.refreshLatencies()
        } finally {
            filters.update { it.copy(measuring = false) }
        }
    }

    // ------------------------------------------------------------------ connection

    /** The big power button: connect when idle/failed, disconnect when active. */
    fun onPowerClick() {
        when (controller.status.value.state) {
            VpnState.IDLE, VpnState.FAILED -> viewModelScope.launch {
                val server = currentSelection()
                if (server != null) requestConnect(server.id)
            }

            VpnState.CONNECTING, VpnState.CONNECTED -> controller.disconnect()
            VpnState.DISCONNECTING -> Unit
        }
    }

    /** Selects a server; if a session is active it is switched over to the new server. */
    fun onServerSelected(server: VpnServer) {
        viewModelScope.launch {
            settings.setSelectedServerId(server.id)
            val status = controller.status.value
            if (status.state.isActive && status.serverId != server.id) requestConnect(server.id)
        }
    }

    /**
     * Smart Connect: picks the lowest-latency reachable server (within the active region
     * filter, if any) and connects to it.
     */
    fun onSmartConnect() {
        viewModelScope.launch {
            val region = filters.value.region
            fun candidates(all: List<VpnServer>) = filterServers(all, region, "")

            var best = smartConnect(candidates(serverRepository.servers.first()))
            if (best == null) {
                measureLatencies()
                // A probe started elsewhere may still be running: wait for it to finish.
                filters.first { !it.measuring }
                best = smartConnect(candidates(serverRepository.servers.first()))
            }
            if (best == null) {
                _events.send(VpnEvent.Message("No server responded to the latency probe"))
                return@launch
            }
            settings.setSelectedServerId(best.id)
            val status = controller.status.value
            if (status.state == VpnState.CONNECTED && status.serverId == best.id) {
                _events.send(VpnEvent.Message("Already on the fastest server: ${best.city}"))
            } else {
                requestConnect(best.id)
            }
        }
    }

    /** Result of the consent dialog launched for [VpnEvent.RequestVpnPermission]. */
    fun onVpnPermissionResult(granted: Boolean) {
        val serverId = pendingServerId
        pendingServerId = null
        if (granted && serverId != null) {
            controller.connect(serverId)
        } else if (!granted) {
            _events.trySend(VpnEvent.Message("VPN permission is required to connect"))
        }
    }

    private suspend fun requestConnect(serverId: String) {
        // VpnService.prepare() returns null once the user has approved this app.
        val consent = controller.prepareIntent()
        if (consent == null) {
            controller.connect(serverId)
        } else {
            pendingServerId = serverId
            _events.send(VpnEvent.RequestVpnPermission(consent))
        }
    }

    private suspend fun currentSelection(): VpnServer? {
        val servers = serverRepository.servers.first()
        val selectedId = settings.selectedServerId.first()
        return servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
    }

    private companion object {
        const val LATENCY_REFRESH_MS = 60_000L
    }
}
