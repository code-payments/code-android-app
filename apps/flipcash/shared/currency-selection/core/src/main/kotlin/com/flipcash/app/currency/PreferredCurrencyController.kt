package com.flipcash.app.currency

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.services.controllers.SettingsController
import com.flipcash.services.user.UserManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.base58
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferredCurrencyController @Inject constructor(
    @ApplicationContext context: Context,
    private val locale: LocaleHelper,
    private val userManager: UserManager,
    private val exchange: Exchange,
    private val settingsController: SettingsController,
) {
    companion object {
        fun initializedKeyForUser(userIdentifier: String) =
            booleanPreferencesKey("init-$userIdentifier")

        fun currencyKeyForUser(userIdentifier: String) =
            stringPreferencesKey("balance-$userIdentifier")

        fun recentsKeyForUser(userIdentifier: String) =
            stringSetPreferencesKey("recents-$userIdentifier")
    }

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storage = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("preferred-currency") }
    )

    init {
        userManager.state
            .mapNotNull { it.accountId }
            .distinctUntilChanged()
            .map { it.base58 }
            .onEach { userId ->
                storage.edit { prefs ->
                    val initKey = initializedKeyForUser(userId)
                    val isInitialized = prefs[initKey] ?: false
                    if (!isInitialized) {
                        val recentKey = recentsKeyForUser(userId)
                        val recents = prefs[recentKey].orEmpty()

                        prefs[recentKey] = updateRecents(recents, locale.getDefaultCurrencyName())
                        prefs[initKey] = true
                    }

                    val currencyCode = prefs[currencyKeyForUser(userId)]
                        ?.let { CurrencyCode.tryValueOf(it) }
                        ?: CurrencyCode.tryValueOf(locale.getDefaultCurrencyName())
                        ?: CurrencyCode.USD

                    exchange.setPreferredCurrency(currencyCode)
                }
            }.launchIn(dataScope)

    }

    fun observePreferredCurrency(): Flow<String> {
        val identifier = userManager.accountId?.base58 ?: return emptyFlow()
        return storage.data.map { prefs ->
            prefs[currencyKeyForUser(identifier)] ?: locale.getDefaultCurrencyName()
        }
    }

    fun observeRecentCurrencies(): Flow<Set<String>> {
        val identifier = userManager.accountId?.base58 ?: return emptyFlow()

        return storage.data.map { prefs -> prefs[recentsKeyForUser(identifier)].orEmpty() }
    }

    suspend fun updateSelection(currency: Currency) {
        val code = CurrencyCode.tryValueOf(currency.code) ?: return
        val identifier = userManager.accountId?.base58 ?: return
        storage.edit { prefs ->
            prefs[currencyKeyForUser(identifier)] = currency.code

            val recentKey = recentsKeyForUser(identifier)
            val recents = prefs[recentKey].orEmpty()

            prefs[recentKey] = updateRecents(recents, currency.code)
        }
        exchange.setPreferredCurrency(code)
        settingsController.update()
    }

    suspend fun removeFromRecents(
        currency: Currency
    ) {
        val identifier = userManager.accountId?.base58 ?: return

        storage.edit { prefs ->
            val recentKey = recentsKeyForUser(identifier)
            val recents = prefs[recentKey].orEmpty()

            prefs[recentKey] = recents - currency.code
        }
    }

    private fun updateRecents(currentSelections: Set<String>, selected: String): Set<String> {
        val updated = LinkedHashSet<String>()

        // Add selected item first if it exists in the set or is new
        updated.add(selected)

        // Add remaining items from currentSelections, excluding selected
        updated.addAll(currentSelections.filter { it != selected })

        return updated
    }
}
