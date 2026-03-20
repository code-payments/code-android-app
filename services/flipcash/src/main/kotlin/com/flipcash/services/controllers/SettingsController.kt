package com.flipcash.services.controllers

import com.flipcash.services.repository.SettingsRepository
import com.flipcash.services.user.UserManager
import com.getcode.util.locale.LocaleHelper
import javax.inject.Inject

class SettingsController @Inject constructor(
    private val repository: SettingsRepository,
    private val localeHelper: LocaleHelper,
    private val userManager: UserManager,
) {
    suspend fun update(): Result<Unit> {
        val locale = localeHelper.getLanguageTag()
        val region = localeHelper.getDefaultCurrencyName()

        println("locale=$locale, region=$region")
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.update(locale, region, owner)
    }
}