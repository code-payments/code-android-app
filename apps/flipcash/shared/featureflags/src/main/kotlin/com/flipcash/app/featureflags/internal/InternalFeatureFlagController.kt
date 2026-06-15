package com.flipcash.app.featureflags.internal

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

internal class InternalFeatureFlagController @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeatureFlagController {

    companion object {
        private val FeatureFlag<*>.booleanPreferenceKey
            get() = booleanPreferencesKey(key)

        private val FeatureFlag<*>.optionPreferenceKey
            get() = stringPreferencesKey("${key}_option")

        private val betaOverrideKey = booleanPreferencesKey("beta_override")
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

        // Clear beta flags restored from backup on fresh install.
        // noBackupFilesDir is never included in Auto Backup, so the marker
        // file won't exist after a restore — triggering a full reset.
        val marker = File(context.noBackupFilesDir, "beta-flags-initialized")
        if (!marker.exists()) {
            reset()
            marker.createNewFile()
        }
    }

    override fun enableBetaFeatures() {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[betaOverrideKey] = true
            }
        }
    }

    override fun disableBetaFeatures() {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[betaOverrideKey] = false
                FeatureFlag.availableEntries.forEach { flag ->
                    prefs.remove(flag.booleanPreferenceKey)
                    if (flag.isOptionFlag) {
                        prefs.remove(flag.optionPreferenceKey)
                    }
                }
            }
        }
    }

    override fun observeOverride(): StateFlow<Boolean> =
        betaFlags.data.map { prefs -> prefs[betaOverrideKey] ?: false }
            .stateIn(dataScope, SharingStarted.Eagerly, false)

    override fun set(flag: FeatureFlag<*>, value: Boolean) {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[flag.booleanPreferenceKey] = value
            }
        }
    }

    override suspend fun get(flag: FeatureFlag<*>): Boolean {
        return betaFlags.data.map { prefs ->
            if (flag.launched) return@map flag.defaultEnabled
            if (flag.isOptionFlag) {
                val option = prefs[flag.optionPreferenceKey] ?: flag.defaultOption
                return@map flag.options.find { it.key == option }?.isDisabled != true
            }
            prefs[flag.booleanPreferenceKey] ?: flag.defaultEnabled
        }.firstOrNull() ?: flag.defaultEnabled
    }

    override fun observe(): StateFlow<List<BetaFeature>> = betaFlags.data.map { prefs ->
        FeatureFlag.availableEntries
            .map { flag ->
                if (flag.isOptionFlag) {
                    val option = prefs[flag.optionPreferenceKey] ?: flag.defaultOption
                    BetaFeature(flag, enabled = flag.options.find { it.key == option }?.isDisabled != true, selectedOption = option)
                } else {
                    val value = if (flag.launched) {
                        flag.defaultEnabled
                    } else {
                        prefs[flag.booleanPreferenceKey] ?: flag.defaultEnabled
                    }
                    BetaFeature(flag, value)
                }
            }
    }.stateIn(
        dataScope,
        started = SharingStarted.Eagerly,
        FeatureFlag.availableEntries
            .map { flag ->
                if (flag.isOptionFlag) {
                    BetaFeature(flag, enabled = flag.defaultEnabled, selectedOption = flag.defaultOption)
                } else {
                    BetaFeature(flag, flag.defaultEnabled)
                }
            }
    )

    override fun observe(flag: FeatureFlag<*>): StateFlow<Boolean> = betaFlags.data.map { prefs ->
        if (flag.launched) return@map flag.defaultEnabled
        if (flag.isOptionFlag) {
            val option = prefs[flag.optionPreferenceKey] ?: flag.defaultOption
            return@map flag.options.find { it.key == option }?.isDisabled != true
        }
        prefs[flag.booleanPreferenceKey] ?: flag.defaultEnabled
    }.stateIn(dataScope, started = SharingStarted.Eagerly, flag.defaultEnabled)

    override fun setOption(flag: FeatureFlag<*>, optionKey: String) {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[flag.optionPreferenceKey] = optionKey
            }
        }
    }

    override fun getOption(flag: FeatureFlag<*>): StateFlow<String> = betaFlags.data.map { prefs ->
        prefs[flag.optionPreferenceKey] ?: flag.defaultOption
    }.stateIn(dataScope, started = SharingStarted.Eagerly, flag.defaultOption)

    override fun reset(flag: FeatureFlag<*>) {
        dataScope.launch {
            betaFlags.edit {
                it.remove(flag.booleanPreferenceKey)
                if (flag.isOptionFlag) {
                    it.remove(flag.optionPreferenceKey)
                }
            }
        }
    }

    override fun reset() {
        dataScope.launch {
            betaFlags.edit { prefs ->
                FeatureFlag.entries
                    .filterNot { it.persistLogOut }
                    .forEach { flag ->
                        prefs.remove(flag.booleanPreferenceKey)
                        if (flag.isOptionFlag) {
                            prefs.remove(flag.optionPreferenceKey)
                        }
                    }

                prefs.remove(betaOverrideKey)
            }
        }
    }
}