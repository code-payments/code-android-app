package com.flipcash.app.userflags

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.services.models.UserFlags
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.financial.Fiat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class UserFlagsCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    userManager: UserManager,
    dispatchers: DispatcherProvider,
) {
    data class Overrides(
        val preferredOnRampProvider: FieldOverride<OnRampProvider.Defined?>,
        val supportedOnRampProviders: FieldOverride<List<OnRampProvider.Defined>>,
        val minimumVersion: FieldOverride<Int?>,
        val billExchangeDataTimeout: FieldOverride<Duration?>,
        val newCurrencyPurchaseAmount: FieldOverride<Fiat>,
        val newCurrencyFeeAmount: FieldOverride<Fiat>,
        val withdrawalFeeAmount: FieldOverride<Fiat>,
        val preferredUsdcOnRampLiquidityPool: FieldOverride<UsdcLiquidtyPool>,
        val minimumHolderAmountForLeaderboard: FieldOverride<Fiat>,
        val requireCoinbaseEmailVerification: FieldOverride<Boolean>,
    ) {
        companion object {
            val None = Overrides(
                preferredOnRampProvider = FieldOverride.None,
                supportedOnRampProviders = FieldOverride.None,
                minimumVersion = FieldOverride.None,
                billExchangeDataTimeout = FieldOverride.None,
                newCurrencyPurchaseAmount = FieldOverride.None,
                newCurrencyFeeAmount = FieldOverride.None,
                withdrawalFeeAmount = FieldOverride.None,
                preferredUsdcOnRampLiquidityPool = FieldOverride.None,
                minimumHolderAmountForLeaderboard = FieldOverride.None,
                requireCoinbaseEmailVerification = FieldOverride.None,
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    init {
        // Delete the backing file before DataStore reads it to avoid a race
        // where stale overrides restored from backup are briefly visible.
        val marker = File(context.noBackupFilesDir, "user-flag-overrides-initialized")
        if (!marker.exists()) {
            context.preferencesDataStoreFile("user-flag-overrides").delete()
            marker.createNewFile()
        }
    }

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("user-flag-overrides") }
    )

    // Parse DataStore prefs → Overrides
    private val overrides: StateFlow<Overrides> = dataStore.data.map { prefs ->
        Overrides(
            preferredOnRampProvider = prefs.readOverride(Field.PreferredProvider),
            supportedOnRampProviders = prefs.readOverride(Field.SupportedProviders),
            minimumVersion = prefs.readOverride(Field.MinimumVersion),
            billExchangeDataTimeout = prefs.readOverride(Field.BillExchangeDataTimeout),
            newCurrencyPurchaseAmount = prefs.readOverride(Field.NewCurrencyPurchaseAmount),
            newCurrencyFeeAmount = prefs.readOverride(Field.NewCurrencyFeeAmount),
            withdrawalFeeAmount = prefs.readOverride(Field.WithdrawalFeeAmount),
            preferredUsdcOnRampLiquidityPool = prefs.readOverride(Field.PreferredUsdcOnRampLiquidityPool),
            minimumHolderAmountForLeaderboard = prefs.readOverride(Field.MinimumHolderAmountForLeaderboard),
            requireCoinbaseEmailVerification = prefs.readOverride(Field.RequireCoinbaseEmailVerification),
        )
    }.stateIn(scope, SharingStarted.Eagerly, Overrides.None)

    val resolvedFlags: StateFlow<ResolvedUserFlags> = combine(
        userManager.state.map { it.flags }.distinctUntilChanged(),
        overrides
    ) { server, overrides ->
        server?.resolve(overrides) ?: UserFlags.Default.resolve(overrides)
    }.stateIn(scope, SharingStarted.Eagerly, UserFlags.Default.resolve(Overrides.None))

    fun <Stored, Domain> set(field: Field<Stored, Domain>, value: Domain) {
        scope.launch {
            dataStore.edit { it[field.preferenceKey] = field.encode(value) }
        }
    }

    fun clear(field: Field<*, *>) {
        scope.launch { dataStore.edit { it.remove(field.preferenceKey) } }
    }

    fun clearAll() {
        scope.launch { dataStore.edit { it.clear() } }
    }
}