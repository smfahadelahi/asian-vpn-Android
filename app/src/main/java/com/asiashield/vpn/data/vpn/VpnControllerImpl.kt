package com.asiashield.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.asiashield.vpn.data.service.VpnConnectionService
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.domain.model.VpnStatus
import com.asiashield.vpn.domain.repository.VpnController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class VpnControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: VpnStateHolder,
) : VpnController {

    override val status: StateFlow<VpnStatus> = stateHolder.status

    override fun prepareIntent(): Intent? = VpnService.prepare(context)

    override fun connect(serverId: String) {
        ContextCompat.startForegroundService(context, VpnConnectionService.connectIntent(context, serverId))
    }

    override fun disconnect() {
        when (stateHolder.state) {
            VpnState.CONNECTING, VpnState.CONNECTED ->
                context.startService(VpnConnectionService.disconnectIntent(context))
            // Nothing is running after a failure: just acknowledge it.
            VpnState.FAILED -> stateHolder.transition(VpnState.IDLE) { VpnStatus() }
            VpnState.IDLE, VpnState.DISCONNECTING -> Unit
        }
    }
}
