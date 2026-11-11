package com.asiashield.vpn.data.latency

import android.os.SystemClock
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures round-trip time to a host with an ICMP echo (falling back to a TCP echo probe
 * when ICMP sockets are unavailable). WireGuard itself is UDP and silent to unauthenticated
 * packets, so the endpoint port cannot be probed directly.
 */
class LatencyTracker @Inject constructor() {

    suspend fun measure(host: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int? =
        withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(host)
                val start = SystemClock.elapsedRealtimeNanos()
                if (address.isReachable(timeoutMs)) {
                    ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L).toInt().coerceAtLeast(1)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1500
    }
}
