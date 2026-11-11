package com.asiashield.vpn.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.asiashield.vpn.R
import com.asiashield.vpn.data.vpn.VpnStateHolder
import com.asiashield.vpn.data.vpn.WireGuardConfigGenerator
import com.asiashield.vpn.data.vpn.WireGuardTunnel
import com.asiashield.vpn.domain.model.VpnServer
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.domain.model.VpnStatus
import com.asiashield.vpn.domain.repository.ServerRepository
import com.asiashield.vpn.domain.repository.SettingsRepository
import com.asiashield.vpn.presentation.MainActivity
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground [VpnService] that owns the VPN session lifecycle
 * (IDLE -> CONNECTING -> CONNECTED -> DISCONNECTING -> IDLE, or FAILED), publishes it
 * through [VpnStateHolder] and mirrors it in an ongoing notification with a one-tap
 * "Disconnect" action.
 *
 * The tunnel is driven through the WireGuard SDK's [GoBackend]. GoBackend creates the TUN
 * interface from its own internal `GoBackend.VpnService` (declared by the library's
 * manifest); this service generates the configuration, sequences the state machine,
 * verifies the handshake and keeps the process in the foreground.
 */
@AndroidEntryPoint
class VpnConnectionService : VpnService() {

    @Inject lateinit var stateHolder: VpnStateHolder
    @Inject lateinit var backend: dagger.Lazy<GoBackend>
    @Inject lateinit var configGenerator: WireGuardConfigGenerator
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Serialises connect / disconnect commands in arrival order. */
    private val commandMutex = Mutex()

    /** GoBackend is not thread-safe: every call into it goes through this lock. */
    private val backendMutex = Mutex()

    private var connectJob: Job? = null
    private var statsJob: Job? = null
    private var lastStartId = 0

    private val tunnel = WireGuardTunnel(TUNNEL_NAME) { newState ->
        if (newState == Tunnel.State.DOWN) onTunnelWentDown()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_CONNECT -> {
                // Must happen promptly after startForegroundService().
                enterForeground()
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                scope.launch { commandMutex.withLock { handleConnect(serverId) } }
            }

            ACTION_DISCONNECT -> scope.launch {
                commandMutex.withLock {
                    performDisconnect()
                    stopIfLatest()
                }
            }

            else -> if (!stateHolder.state.isActive) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // If the system tears the service down mid-session, do not leave a stale tunnel
        // or a stale "connected" state behind.
        val state = stateHolder.state
        if (state.isActive || state == VpnState.DISCONNECTING) {
            if (state.isActive) stateHolder.transition(VpnState.DISCONNECTING)
            stateHolder.transition(VpnState.IDLE) { VpnStatus() }
            thread(name = "wg-teardown") {
                runCatching { backend.get().setState(tunnel, Tunnel.State.DOWN, null) }
            }
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- state machine

    private suspend fun handleConnect(serverId: String?) {
        val server = serverId?.let(serverRepository::getServer)
        if (server == null) {
            Log.e(TAG, "Connect requested for unknown server '$serverId'")
            if (!stateHolder.state.isActive) stopIfLatest()
            return
        }
        // Switching servers: bring the current session down first.
        if (stateHolder.state.isActive) performDisconnect()

        val started = stateHolder.transition(VpnState.CONNECTING) {
            VpnStatus(serverId = server.id)
        }
        if (!started) {
            stopIfLatest()
            return
        }
        updateNotification()
        connectJob = scope.launch { runConnect(server) }
    }

    private suspend fun runConnect(server: VpnServer) {
        try {
            val privateKey = settings.getOrCreatePrivateKey()
            val config = configGenerator.generate(privateKey, server)
            withContext(Dispatchers.IO) {
                backendMutex.withLock {
                    ensureActive()
                    backend.get().setState(tunnel, Tunnel.State.UP, config)
                }
            }
            awaitHandshake()
            val connected = stateHolder.transition(VpnState.CONNECTED) {
                it.copy(connectedSinceElapsedMs = SystemClock.elapsedRealtime(), errorMessage = null)
            }
            if (connected) {
                updateNotification()
                startStatsLoop()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Connection to ${server.id} failed", e)
            failAndStop(describeError(e, server))
        }
    }

    /**
     * A WireGuard interface comes "up" even when the peer never answers, so only report
     * CONNECTED once bytes have actually been received from the server.
     */
    private suspend fun awaitHandshake() {
        val ok = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
            while (readTotals().first <= 0L) delay(HANDSHAKE_POLL_MS)
            true
        }
        if (ok != true) throw IOException("No response from server (handshake timed out)")
    }

    private suspend fun performDisconnect() {
        if (!stateHolder.transition(VpnState.DISCONNECTING)) return
        updateNotification()
        statsJob?.cancel()
        statsJob = null
        connectJob?.cancelAndJoin()
        connectJob = null
        bringTunnelDown()
        stateHolder.transition(VpnState.IDLE) { VpnStatus() }
    }

    private suspend fun failAndStop(message: String) {
        withContext(NonCancellable) {
            statsJob?.cancel()
            statsJob = null
            bringTunnelDown()
            stateHolder.transition(VpnState.FAILED) {
                VpnStatus(state = VpnState.FAILED, serverId = it.serverId, errorMessage = message)
            }
            stopIfLatest()
        }
    }

    /** Called by GoBackend when the tunnel is torn down, e.g. another VPN app took over. */
    private fun onTunnelWentDown() {
        scope.launch {
            if (stateHolder.state == VpnState.CONNECTED) {
                failAndStop("VPN was stopped by the system or another VPN app")
            }
        }
    }

    private suspend fun bringTunnelDown() {
        withContext(NonCancellable + Dispatchers.IO) {
            backendMutex.withLock {
                try {
                    backend.get().setState(tunnel, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to bring tunnel down", e)
                }
            }
        }
    }

    private fun stopIfLatest() {
        // No-op when a newer start command is pending, so a queued connect is not lost.
        if (stopSelfResult(lastStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            updateNotification()
        }
    }

    // ---------------------------------------------------------------- statistics

    /** Returns (received, sent) byte totals for the tunnel. */
    private suspend fun readTotals(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        backendMutex.withLock {
            val stats = backend.get().getStatistics(tunnel)
            stats.totalRx() to stats.totalTx()
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var (lastRx, lastTx) = try {
                readTotals()
            } catch (e: Exception) {
                0L to 0L
            }
            var lastAt = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(STATS_INTERVAL_MS)
                val (rx, tx) = try {
                    readTotals()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - lastAt).coerceAtLeast(1)
                stateHolder.updateTraffic(
                    rxBytes = rx,
                    txBytes = tx,
                    rxBytesPerSec = (rx - lastRx).coerceAtLeast(0) * 1000 / elapsedMs,
                    txBytesPerSec = (tx - lastTx).coerceAtLeast(0) * 1000 / elapsedMs,
                )
                lastRx = rx
                lastTx = tx
                lastAt = now
            }
        }
    }

    private fun describeError(e: Exception, server: VpnServer): String = when (e) {
        is BackendException -> when (e.reason) {
            BackendException.Reason.VPN_NOT_AUTHORIZED -> "VPN permission has not been granted"
            BackendException.Reason.DNS_RESOLUTION_FAILURE -> "Could not resolve ${server.host}"
            BackendException.Reason.UNABLE_TO_START_VPN -> "Android could not start the VPN service"
            BackendException.Reason.TUN_CREATION_ERROR -> "Could not create the VPN interface"
            else -> "WireGuard error: ${e.reason.name}"
        }
        is BadConfigException -> "Invalid server configuration for ${server.city}"
        else -> e.message ?: "Connection failed"
    }

    // ---------------------------------------------------------------- notification

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val status = stateHolder.status.value
        val server = status.serverId?.let(serverRepository::getServer)
        val where = server?.let { "${it.flagEmoji} ${it.city}, ${it.country}" }.orEmpty()
        val text = when (status.state) {
            VpnState.CONNECTING -> "Connecting to $where…"
            VpnState.CONNECTED -> "Connected to $where"
            VpnState.DISCONNECTING -> "Disconnecting…"
            VpnState.FAILED -> status.errorMessage ?: "Connection failed"
            VpnState.IDLE -> "Starting…"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(getColor(R.color.brand_teal))

        if (status.state.isActive) {
            val disconnect = PendingIntent.getService(
                this,
                1,
                disconnectIntent(this),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, getString(R.string.action_disconnect), disconnect)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "VpnConnectionService"
        private const val TUNNEL_NAME = "asiashield"
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1001
        private const val HANDSHAKE_TIMEOUT_MS = 12_000L
        private const val HANDSHAKE_POLL_MS = 400L
        private const val STATS_INTERVAL_MS = 1_000L

        const val ACTION_CONNECT = "com.asiashield.vpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.asiashield.vpn.action.DISCONNECT"
        const val EXTRA_SERVER_ID = "server_id"

        fun connectIntent(context: Context, serverId: String): Intent =
            Intent(context, VpnConnectionService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_SERVER_ID, serverId)

        fun disconnectIntent(context: Context): Intent =
            Intent(context, VpnConnectionService::class.java).setAction(ACTION_DISCONNECT)
    }
}
