package com.asiashield.vpn.data.vpn

import com.asiashield.vpn.domain.model.VpnServer
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import javax.inject.Inject

/** Builds the WireGuard tunnel configuration for a given server. */
class WireGuardConfigGenerator @Inject constructor() {

    /** Renders the configuration in wg-quick format. */
    fun generateText(
        privateKey: String,
        server: VpnServer,
        address: String = DEFAULT_ADDRESS,
        dns: String = DEFAULT_DNS,
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKey")
        appendLine("Address = $address")
        appendLine("DNS = $dns")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${server.publicKey}")
        appendLine("Endpoint = ${server.endpoint}")
        appendLine("AllowedIPs = $ALLOWED_IPS")
        appendLine("PersistentKeepalive = $KEEPALIVE_SECONDS")
    }

    /** @throws com.wireguard.config.BadConfigException if a key, address or endpoint is malformed. */
    fun generate(privateKey: String, server: VpnServer): Config =
        Config.parse(ByteArrayInputStream(generateText(privateKey, server).toByteArray(Charsets.UTF_8)))

    companion object {
        const val DEFAULT_ADDRESS = "10.66.66.2/32"
        const val DEFAULT_DNS = "1.1.1.1"
        const val ALLOWED_IPS = "0.0.0.0/0"
        const val KEEPALIVE_SECONDS = 25
    }
}
