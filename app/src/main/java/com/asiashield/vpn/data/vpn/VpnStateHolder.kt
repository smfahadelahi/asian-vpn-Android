package com.asiashield.vpn.data.vpn

import android.util.Log
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.domain.model.VpnStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide source of truth for the VPN state machine. */
@Singleton
class VpnStateHolder @Inject constructor() {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    val state: VpnState get() = _status.value.state

    /**
     * Atomically moves to [next] if the state machine allows it, applying [transform] to
     * the rest of the status. Returns false (and changes nothing) for illegal transitions.
     */
    fun transition(next: VpnState, transform: (VpnStatus) -> VpnStatus = { it }): Boolean {
        while (true) {
            val current = _status.value
            if (!current.state.canTransitionTo(next)) {
                Log.w(TAG, "Ignored illegal transition ${current.state} -> $next")
                return false
            }
            if (_status.compareAndSet(current, transform(current).copy(state = next))) {
                Log.i(TAG, "${current.state} -> $next")
                return true
            }
        }
    }

    fun updateTraffic(rxBytes: Long, txBytes: Long, rxBytesPerSec: Long, txBytesPerSec: Long) {
        _status.update {
            if (it.state == VpnState.CONNECTED) {
                it.copy(
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    rxBytesPerSec = rxBytesPerSec,
                    txBytesPerSec = txBytesPerSec,
                )
            } else {
                it
            }
        }
    }

    private companion object {
        const val TAG = "VpnStateHolder"
    }
}
