package com.asiashield.vpn.domain.model

data class VpnServer(
    val id: String,
    /** ISO 3166-1 alpha-2 country code, e.g. "JP". */
    val countryCode: String,
    val country: String,
    val city: String,
    val region: AsiaRegion,
    /** Hostname or IP address of the WireGuard endpoint. */
    val host: String,
    val port: Int,
    /** Base64 WireGuard public key of the peer. */
    val publicKey: String,
    val flagEmoji: String = flagEmojiOf(countryCode),
    /** Last measured round-trip time, or null if unreachable / not measured yet. */
    val latencyMs: Int? = null,
    /** True once at least one latency probe has completed for this server. */
    val latencyChecked: Boolean = false,
) {
    val endpoint: String get() = "$host:$port"
    val displayName: String get() = "$country · $city"
}

/** Converts an ISO country code into its regional-indicator flag emoji. */
fun flagEmojiOf(countryCode: String): String {
    if (countryCode.length != 2) return "\uD83C\uDFF3\uFE0F"
    val base = 0x1F1E6
    return countryCode.uppercase().map { ch ->
        String(Character.toChars(base + (ch - 'A')))
    }.joinToString("")
}
