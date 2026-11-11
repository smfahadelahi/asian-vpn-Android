package com.asiashield.vpn.data.repository

import com.asiashield.vpn.data.latency.LatencyTracker
import com.asiashield.vpn.domain.model.AsiaRegion
import com.asiashield.vpn.domain.model.AsiaRegion.CENTRAL_ASIA
import com.asiashield.vpn.domain.model.AsiaRegion.EAST_ASIA
import com.asiashield.vpn.domain.model.AsiaRegion.MIDDLE_EAST
import com.asiashield.vpn.domain.model.AsiaRegion.SOUTHEAST_ASIA
import com.asiashield.vpn.domain.model.AsiaRegion.SOUTH_ASIA
import com.asiashield.vpn.domain.model.VpnServer
import com.asiashield.vpn.domain.repository.ServerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Curated catalogue of Asian exit nodes plus a live latency (RTT) tracker.
 *
 * IMPORTANT: the hosts below use the reserved `.example` TLD and the public keys are
 * random placeholders. Replace both with the endpoints and keys of real WireGuard
 * servers that you operate; until then connections will fail at DNS resolution.
 */
@Singleton
class AsiaServerRepository @Inject constructor(
    private val latencyTracker: LatencyTracker,
) : ServerRepository {

    /** serverId -> last RTT in ms (null value = probed but unreachable). */
    private val latencies = MutableStateFlow<Map<String, Int?>>(emptyMap())

    override val servers: Flow<List<VpnServer>> = latencies.map { measured ->
        CATALOG.map { server ->
            if (server.id in measured) {
                server.copy(latencyMs = measured[server.id], latencyChecked = true)
            } else {
                server
            }
        }
    }

    override fun getServer(id: String): VpnServer? = CATALOG.firstOrNull { it.id == id }

    override suspend fun refreshLatencies() = coroutineScope {
        CATALOG.forEach { server ->
            launch {
                val rtt = latencyTracker.measure(server.host)
                latencies.update { it + (server.id to rtt) }
            }
        }
    }

    companion object {
        private const val WG_PORT = 51820

        private fun node(
            id: String,
            code: String,
            country: String,
            city: String,
            region: AsiaRegion,
            publicKey: String,
        ) = VpnServer(
            id = id,
            countryCode = code,
            country = country,
            city = city,
            region = region,
            host = "$id.asiashield.example",
            port = WG_PORT,
            publicKey = publicKey,
        )

        val CATALOG: List<VpnServer> = listOf(
            // East Asia
            node("jp-tyo", "JP", "Japan", "Tokyo", EAST_ASIA, "NZj84qrOYvNJG4I6LksLB7G+v6dZQjFrSL8lNWf146o="),
            node("jp-osa", "JP", "Japan", "Osaka", EAST_ASIA, "zjgnUSovJ9T3ILRPqElzKwDY0Hp73mdTzxH97RUlyUg="),
            node("kr-sel", "KR", "South Korea", "Seoul", EAST_ASIA, "H74r+UDEiJIivnnaNhkD6yFKMLEHy4Afzo6McQEZ5oA="),
            node("tw-tpe", "TW", "Taiwan", "Taipei", EAST_ASIA, "bpyuUdLBoTdj9P0TVoA4Aahi5C9DhrZ2C4ZujSGCwXs="),
            node("hk-hkg", "HK", "Hong Kong", "Hong Kong", EAST_ASIA, "o2XPNQOLt2/d61yf8khH8aDvUpzPCqHOz97TFGILh5I="),
            // Southeast Asia
            node("sg-sin", "SG", "Singapore", "Singapore", SOUTHEAST_ASIA, "NeQaSTknOh1K0nwAU9VzKsEaoBC8lnobha3Noj/pddQ="),
            node("my-kul", "MY", "Malaysia", "Kuala Lumpur", SOUTHEAST_ASIA, "ux8fFHhWv0ENp/slTcqbLNkinntJfJfX8gqrJPzhYgI="),
            node("id-jkt", "ID", "Indonesia", "Jakarta", SOUTHEAST_ASIA, "zsrzJlzBz41426SAe9Aoe9gqy8bGD7SZWMRXYReNe/Y="),
            node("th-bkk", "TH", "Thailand", "Bangkok", SOUTHEAST_ASIA, "hJsqUdpTKqErnsMoNpXWv78mftg/xaO4SLEa93AexNI="),
            node("vn-sgn", "VN", "Vietnam", "Ho Chi Minh City", SOUTHEAST_ASIA, "fqMg9c9/5h4hpYR3v0/6u2uPEiQnx30A5QcFuYhmhq0="),
            node("ph-mnl", "PH", "Philippines", "Manila", SOUTHEAST_ASIA, "6icm3NXijrscPpqNp55FGgfyvHe/MBcb3Fvr9Tq+Zcc="),
            // South Asia
            node("in-bom", "IN", "India", "Mumbai", SOUTH_ASIA, "jeVehedE1Zb6rNJzCpOF536DNgghggrJ5+GnC8ayOBw="),
            node("bd-dac", "BD", "Bangladesh", "Dhaka", SOUTH_ASIA, "dIM97HnAgodzwusTIBn/cleR1PFUwIoUHXPKUbhae18="),
            node("pk-khi", "PK", "Pakistan", "Karachi", SOUTH_ASIA, "WuXll7ajMUsRs8vRw7suy5uUcOZmsYkwdlzxIuD12IQ="),
            node("lk-cmb", "LK", "Sri Lanka", "Colombo", SOUTH_ASIA, "WKkUbcg6zCkNc897uX7x5qCyk0S78dfEOpQaVbYmE6g="),
            node("np-ktm", "NP", "Nepal", "Kathmandu", SOUTH_ASIA, "cgbK+uUWs8JzJQdyH8aCHEVnrcf6Uht69PwLX40Rbp4="),
            // Central Asia
            node("kz-ala", "KZ", "Kazakhstan", "Almaty", CENTRAL_ASIA, "r6/EaQqKf5OVA3eK74ZFHu2jlfaq0vMXSduLZlvcgFw="),
            node("uz-tas", "UZ", "Uzbekistan", "Tashkent", CENTRAL_ASIA, "nJljoPZYTymeYnq08cFyTE/oWw9MvLOH+RKTRpahor4="),
            node("kg-fru", "KG", "Kyrgyzstan", "Bishkek", CENTRAL_ASIA, "b6wxu2jhv4UZNSF265e4pcb8Xy7HYMRWdiLSPJTUP9A="),
            // Middle East
            node("ae-dxb", "AE", "UAE", "Dubai", MIDDLE_EAST, "nqVsWcTEvmhiFM/5ON3ADOM/QX28qZ0/K0/nLz/sV6E="),
            node("sa-ruh", "SA", "Saudi Arabia", "Riyadh", MIDDLE_EAST, "u476Y2SiXrCyy/9iHaAZ7L5vccaNhXdvVATp94rDbyo="),
            node("qa-doh", "QA", "Qatar", "Doha", MIDDLE_EAST, "e0fse1j+5Dfk7OIGY0bN6jv0QVek99iJtCtriEOeHFM="),
            node("tr-ist", "TR", "Turkey", "Istanbul", MIDDLE_EAST, "Aog1A11FwWTlckJXbeBB/6XGVwpJznk9IqTqc+xhmPs="),
        )
    }
}
