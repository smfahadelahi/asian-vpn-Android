package com.asiashield.vpn.domain.usecase

import com.asiashield.vpn.domain.model.AsiaRegion
import com.asiashield.vpn.domain.model.VpnServer
import javax.inject.Inject

class FilterServersUseCase @Inject constructor() {
    /** Filters by [region] (null = all) and a case-insensitive free-text [query]. */
    operator fun invoke(servers: List<VpnServer>, region: AsiaRegion?, query: String): List<VpnServer> {
        val q = query.trim()
        return servers.filter { server ->
            (region == null || server.region == region) &&
                (q.isEmpty() ||
                    server.country.contains(q, ignoreCase = true) ||
                    server.city.contains(q, ignoreCase = true) ||
                    server.countryCode.equals(q, ignoreCase = true) ||
                    server.region.label.contains(q, ignoreCase = true))
        }
    }
}
