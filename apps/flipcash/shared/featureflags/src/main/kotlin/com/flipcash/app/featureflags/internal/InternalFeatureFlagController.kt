package com.flipcash.app.featureflags.internal

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class InternalFeatureFlagController @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeatureFlagController {

    companion object {
        private val FeatureFlag.booleanPreferenceKey
            get() = booleanPreferencesKey(key)
    }

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val betaFlags = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("beta-flags") }
    )

    init {
        // reset launched flags
        FeatureFlag.entries
            .filter { it.launched }
            .onEach { reset(it) }
    }

    override fun set(flag: FeatureFlag, value: Boolean) {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[flag.booleanPreferenceKey] = value
            }
        }
    }

    override suspend fun get(flag: FeatureFlag): Boolean {
        return betaFlags.data.map { prefs ->
            if (flag.launched) return@map flag.default
            prefs[flag.booleanPreferenceKey] ?: flag.default
        }.firstOrNull() ?: flag.default
    }

    override fun observe(): StateFlow<List<BetaFeature>> = betaFlags.data.map { prefs ->
        FeatureFlag.entries.filterNot { it.launched }.map {
            val value = if (it.launched) {
                it.default
            } else {
                prefs[it.booleanPreferenceKey] ?: it.default
            }

            BetaFeature(it, value)
        }
    }.stateIn(
        dataScope,
        started = SharingStarted.Eagerly,
        FeatureFlag.entries.map { BetaFeature(it, it.default) }
    )

    override fun observe(flag: FeatureFlag): StateFlow<Boolean> = betaFlags.data.map { prefs ->
        if (flag.launched) return@map flag.default
        prefs[flag.booleanPreferenceKey] ?: flag.default
    }.stateIn(dataScope, started = SharingStarted.Eagerly, flag.default)

    override fun reset(flag: FeatureFlag) {
        dataScope.launch {
            betaFlags.edit { it.remove(flag.booleanPreferenceKey) }
        }
    }

    override fun reset() {
        dataScope.launch {
            betaFlags.edit { it.clear() }
        }
    }
}