package com.asiashield.vpn.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asiashield.vpn.domain.repository.SettingsRepository
import com.wireguard.crypto.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val store get() = context.settingsDataStore

    override val selectedServerId: Flow<String?> = store.data.map { it[KEY_SELECTED_SERVER] }

    override suspend fun setSelectedServerId(id: String) {
        store.edit { it[KEY_SELECTED_SERVER] = id }
    }

    override suspend fun getOrCreatePrivateKey(): String {
        // edit {} is transactional, so concurrent callers always agree on one key.
        val prefs = store.edit { prefs ->
            if (prefs[KEY_PRIVATE_KEY].isNullOrBlank()) {
                prefs[KEY_PRIVATE_KEY] = KeyPair().privateKey.toBase64()
            }
        }
        return checkNotNull(prefs[KEY_PRIVATE_KEY])
    }

    private companion object {
        val KEY_SELECTED_SERVER = stringPreferencesKey("selected_server_id")
        val KEY_PRIVATE_KEY = stringPreferencesKey("wg_private_key")
    }
}
