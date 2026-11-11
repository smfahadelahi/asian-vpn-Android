package com.asiashield.vpn.di

import android.content.Context
import com.asiashield.vpn.data.repository.AsiaServerRepository
import com.asiashield.vpn.data.settings.DataStoreSettingsRepository
import com.asiashield.vpn.data.vpn.VpnControllerImpl
import com.asiashield.vpn.domain.repository.ServerRepository
import com.asiashield.vpn.domain.repository.SettingsRepository
import com.asiashield.vpn.domain.repository.VpnController
import com.wireguard.android.backend.GoBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindServerRepository(impl: AsiaServerRepository): ServerRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindVpnController(impl: VpnControllerImpl): VpnController

    companion object {
        /** Userspace WireGuard backend. Must be a singleton: it owns the tunnel handle. */
        @Provides
        @Singleton
        fun provideGoBackend(@ApplicationContext context: Context): GoBackend = GoBackend(context)
    }
}
