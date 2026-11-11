package com.asiashield.vpn.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val selectedServerId: Flow<String?>

    suspend fun setSelectedServerId(id: String)

    /** Returns this device's WireGuard private key, generating and persisting one on first use. */
    suspend fun getOrCreatePrivateKey(): String
}
