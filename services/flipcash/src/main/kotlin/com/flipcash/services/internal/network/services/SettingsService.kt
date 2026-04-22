package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.settings.v1.SettingsService
import com.flipcash.services.internal.network.api.SettingsApi
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.UpdateSettingsError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject

internal class SettingsService @Inject constructor(
    private val api: SettingsApi,
) {
    suspend fun update(
        locale: String?,
        region: String?,
        owner: Ed25519.KeyPair,
    ): Result<Unit> {
        return runCatching {
            api.update(locale, region, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    SettingsService.UpdateSettingsResponse.Result.OK -> Result.success(Unit)
                    SettingsService.UpdateSettingsResponse.Result.DENIED -> Result.failure(UpdateSettingsError.Denied())
                    SettingsService.UpdateSettingsResponse.Result.INVALID_LOCALE -> Result.failure(UpdateSettingsError.InvalidLocale())
                    SettingsService.UpdateSettingsResponse.Result.INVALID_REGION -> Result.failure(UpdateSettingsError.InvalidRegion())
                    SettingsService.UpdateSettingsResponse.Result.UNRECOGNIZED -> Result.failure(UpdateSettingsError.Unrecognized())
                }
            },
            onFailure = {
                Result.failure(it.toValidationOrElse { cause -> UpdateSettingsError.Other(cause) })
            }
        )
    }
}