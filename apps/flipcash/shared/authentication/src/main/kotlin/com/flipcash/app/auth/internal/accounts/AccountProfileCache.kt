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
import com.getcode.opencode.model.core.NoId
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
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

/** What this device remembers about an account from the last time it was signed in. */
data class CachedAccountProfile(
    /** The account's user id, which `GetProfile` is keyed by. Null until it signs in here. */
    val userId: ID?,
    val name: AccountProfileName?,
)

/**
 * The last known username and display name of every account that has signed in on this device,
 * keyed by owner public key (base58).
 *
 * The user id cached here is what lets the account list fetch another account's profile with
 * `GetProfile` directly. Only an account with no cached user id goes through the Login RPC to
 * resolve one, and that id is cached too, so each account needs Login at most once on this device.
 *
 * Written from [UserManager]'s state, which every profile change of the signed-in account goes
 * through — the restore at sign-in, the server refresh, and edits — and from each profile and user
 * id the account list fetches.
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
        scope.launch {
            userManager.state
                .mapNotNull { state ->
                    userIdFor(
                        owner = state.cluster?.authorityPublicKey?.base58(),
                        accountId = state.accountId,
                    )
                }
                .distinctUntilChanged()
                .collect { (owner, userId) -> persistUserId(owner, userId) }
        }
    }

    /** Everything cached, by owner public key. Empty when the file cannot be read. */
    suspend fun all(): Map<String, CachedAccountProfile> =
        runCatching { dataStore.data.first().toEntries() }
            .onFailure { error ->
                trace(tag = TAG, message = "Could not read account profiles", error = error, type = TraceType.Error)
            }
            .getOrDefault(emptyMap())

    /** Records a name fetched for [owner], so the next offline visit still has it. */
    suspend fun put(owner: String, name: AccountProfileName) = persist(owner, name)

    /** Records the user id Login resolved for [owner], so later fetches skip Login. */
    suspend fun putUserId(owner: String, userId: ID) = persistUserId(owner, userId)

    private suspend fun persistUserId(owner: String, userId: ID) {
        runCatching {
            dataStore.edit { prefs -> prefs[userIdKey(owner)] = userId.hexEncodedString() }
        }.onFailure { error ->
            trace(tag = TAG, message = "Could not cache an account's user id", error = error, type = TraceType.Error)
        }
    }

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
        private const val USER_ID = "userId:"

        private fun usernameKey(owner: String) = stringPreferencesKey(USERNAME + owner)
        private fun displayNameKey(owner: String) = stringPreferencesKey(DISPLAY_NAME + owner)
        private fun userIdKey(owner: String) = stringPreferencesKey(USER_ID + owner)

        private val PREFIXES = listOf(USERNAME, DISPLAY_NAME, USER_ID)

        private fun Preferences.toEntries(): Map<String, CachedAccountProfile> {
            val values = asMap().mapKeys { it.key.name }
            val owners = values.keys.mapNotNull { key ->
                PREFIXES.firstOrNull { key.startsWith(it) }?.let { key.removePrefix(it) }
            }.toSet()
            return owners.associateWith { owner ->
                val username = values[USERNAME + owner] as? String
                val displayName = values[DISPLAY_NAME + owner] as? String
                CachedAccountProfile(
                    userId = (values[USER_ID + owner] as? String)?.let(::decodeUserId),
                    name = if (username == null && displayName == null) null
                        else AccountProfileName(username = username, displayName = displayName),
                )
            }
        }

        /** Null for anything that is not an even run of hex digits, so a bad entry is just absent. */
        internal fun decodeUserId(hex: String): ID? {
            if (hex.isEmpty() || hex.length % 2 != 0) return null
            return hex.chunked(2).map { it.toIntOrNull(16)?.toByte() ?: return null }
        }

        /**
         * The user id to record for this state, or null when the state does not tie one to an
         * owner key. [UserManager.clear] resets both at sign-out, so a pair seen here belongs to
         * the same session; [NoId], the reset value, is empty and so is skipped.
         */
        internal fun userIdFor(owner: String?, accountId: ID?): Pair<String, ID>? {
            if (owner == null || accountId.isNullOrEmpty()) return null
            return owner to accountId
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
