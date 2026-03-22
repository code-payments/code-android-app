package com.flipcash.services.controllers

import com.flipcash.services.repository.SettingsRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.trace
import javax.inject.Inject

class SettingsController @Inject constructor(
    private val repository: SettingsRepository,
    private val localeHelper: LocaleHelper,
    private val userManager: UserManager,
    private val exchange: Exchange,
) {
    suspend fun update(): Result<Unit> {
        val locale = localeHelper.getLanguageTag()
        val currencyRegion = exchange.balanceRate.currency
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        trace(
            tag = "Settings",
            message = "Updating settings",
            metadata = {
                "locale" to locale
                "region" to currencyRegion.name
            }
        )
        return repository.update(locale, currencyRegion.name, owner)
    }
}