package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.SettingsService
import com.flipcash.services.repository.SettingsRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalSettingsRepository(
    private val service: SettingsService,
) : SettingsRepository {
    override suspend fun update(
        locale: String?,
        region: String?,
        owner: Ed25519.KeyPair
    ): Result<Unit> = service.update(locale, region, owner)
        .onFailure { ErrorUtils.handleError(it) }
}