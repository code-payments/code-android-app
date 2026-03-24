package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.settings.v1.SettingsGrpcKt
import com.codeinc.flipcash.gen.settings.v1.SettingsService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SettingsApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = SettingsGrpcKt.SettingsCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun update(
        locale: String?,
        region: String?,
        owner: Ed25519.KeyPair,
    ): SettingsService.UpdateSettingsResponse {
        val request = SettingsService.UpdateSettingsRequest.newBuilder()
            .apply {
                if (locale != null) {
                    setLocale(Common.Locale.newBuilder().setValue(locale))
                }

                if (region != null) {
                    setRegion(Common.Region.newBuilder().setValue(region.lowercase()))
                }
            }
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.updateSettings(request)
        }
    }
}