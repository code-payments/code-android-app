package com.flipcash.app.auth.internal.accounts

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The part of an account's profile the account list names it by. */
data class AccountProfileName(
    val username: String?,
    val displayName: String?,
)

/**
 * The last known username and display name of every account that has signed in on this device,
 * keyed by owner public key (base58).
 *
 * Only the signed-in account has a session, so this is how the account list names the others
 * without the network and without signing as them. It is written from [UserManager]'s state, which
 * every profile change goes through — the restore at sign-in, the server refresh, and edits — so
 * the entry is as fresh as the last time that account was signed in.
 *
 * Kept out of the Block Store entry on purpose: that one has a 4KB budget sized for fixed-width
 * records, and a name costs nothing to lose — a missing entry falls back to the mnemonic name. The
 * key is the public owner key rather than the entropy so this file never holds a seed.
 */
@Singleton
class AccountProfileCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    userManager: UserManager,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("account-profiles") }
    )

    init {
        scope.launch {
            userManager.state
                .mapNotNull { state ->
                    entryFor(
                        owner = state.cluster?.authorityPublicKey?.base58(),
                        accountId = state.accountId,
                        profile = state.userProfile,
                    )
                }
                .distinctUntilChanged()
                .collect { (owner, name) -> persist(owner, name) }
        }
    }

    /** Every cached name, by owner public key. Empty when the file cannot be read. */
    suspend fun all(): Map<String, AccountProfileName> =
        runCatching { dataStore.data.first().toNames() }
            .onFailure { error ->
                trace(tag = TAG, message = "Could not read account profiles", error = error, type = TraceType.Error)
            }
            .getOrDefault(emptyMap())

    private suspend fun persist(owner: String, name: AccountProfileName) {
        runCatching {
            dataStore.edit { prefs ->
                prefs.putOrRemove(usernameKey(owner), name.username)
                prefs.putOrRemove(displayNameKey(owner), name.displayName)
            }
        }.onFailure { error ->
            trace(tag = TAG, message = "Could not cache an account profile", error = error, type = TraceType.Error)
        }
    }

    private fun MutablePreferences.putOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value.isNullOrBlank()) remove(key) else set(key, value)
    }

    companion object {
        private const val TAG = "AccountProfileCache"
        private const val USERNAME = "username:"
        private const val DISPLAY_NAME = "displayName:"

        private fun usernameKey(owner: String) = stringPreferencesKey(USERNAME + owner)
        private fun displayNameKey(owner: String) = stringPreferencesKey(DISPLAY_NAME + owner)

        private fun Preferences.toNames(): Map<String, AccountProfileName> {
            val values = asMap().mapKeys { it.key.name }
            val owners = values.keys.mapNotNull { key ->
                key.removePrefix(USERNAME).takeIf { key.startsWith(USERNAME) }
                    ?: key.removePrefix(DISPLAY_NAME).takeIf { key.startsWith(DISPLAY_NAME) }
            }.toSet()
            return owners.associateWith { owner ->
                AccountProfileName(
                    username = values[USERNAME + owner] as? String,
                    displayName = values[DISPLAY_NAME + owner] as? String,
                )
            }
        }

        /**
         * The entry to write for this state, or null when there is nothing to attribute.
         *
         * A server-fetched profile carries its user id; one that disagrees with the signed-in
         * account is a fetch that outlived an account switch, and writing it would name one
         * account after another. A profile with no id (the local restore) belongs to whoever is
         * signed in, because the restore slot is wiped at logout.
         */
        internal fun entryFor(
            owner: String?,
            accountId: ID?,
            profile: UserProfile?,
        ): Pair<String, AccountProfileName>? {
            if (owner == null || profile == null) return null
            val profileId = profile.userId
            if (profileId != null && accountId != null && profileId != accountId) return null
            return owner to AccountProfileName(
                username = profile.username?.takeIf { it.isNotBlank() },
                displayName = profile.displayName.takeIf { it.isNotBlank() },
            )
        }
    }
}
