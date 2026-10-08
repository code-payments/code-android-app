package com.flipcash.app.appsettings.internal

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.flipcash.app.core.links.TrustedWebsite
import com.flipcash.app.core.links.TrustedWebsites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * The trusted-website list, in its own Preferences file: one key per host, holding the time it was
 * added. Its own file rather than `app-settings`, because those values are all booleans.
 *
 * Device-level on purpose. Nothing on the logout path clears it, so the list outlives Log Out and
 * Switch Accounts; do not register it with `AuthManager.resetStateForUser`.
 */
internal class InternalTrustedWebsites(
    private val scope: CoroutineScope,
    produceFile: () -> File,
    private val now: () -> Long = System::currentTimeMillis,
) : TrustedWebsites {

    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        scope = scope,
        produceFile = produceFile,
    )

    override val websites: StateFlow<List<TrustedWebsite>> = store.data
        .map { it.toWebsites() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun trust(host: String) {
        scope.launch {
            store.edit { prefs -> prefs[longPreferencesKey(host)] = now() }
        }
    }

    override fun remove(host: String) {
        scope.launch {
            store.edit { prefs -> prefs.remove(longPreferencesKey(host)) }
        }
    }

    private fun Preferences.toWebsites(): List<TrustedWebsite> = asMap()
        .mapNotNull { (key, value) -> (value as? Long)?.let { TrustedWebsite(key.name, it) } }
        .sortedByDescending { it.addedAtMillis }

    companion object {
        const val FILE_NAME = "trusted-websites"
    }
}
