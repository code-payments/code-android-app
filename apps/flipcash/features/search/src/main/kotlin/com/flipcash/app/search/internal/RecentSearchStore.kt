package com.flipcash.app.search.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.getcode.utils.hexEncodedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_search")

/**
 * The chats opened from Search, newest first, for the Recent row. Kept per account, so switching
 * back to one finds its own recents.
 */
@Singleton
internal class RecentSearchStore @Inject constructor(
    @ApplicationContext context: Context,
    private val userManager: UserManager,
) {
    private val dataStore = context.recentSearchDataStore

    private val owner: Flow<String?> =
        userManager.state.map { it.accountId?.hexEncodedString() }.distinctUntilChanged()

    /** The current account's recents, newest first. Empty when nobody is signed in. */
    val recents: Flow<List<ChatId>> = combine(owner, dataStore.data) { owner, prefs ->
        owner ?: return@combine emptyList()
        decode(prefs[key(owner)])
    }.distinctUntilChanged()

    /** Moves [chatId] to the front, dropping the oldest past [RECENT_LIMIT]. */
    suspend fun record(chatId: ChatId) {
        val owner = userManager.accountId?.hexEncodedString() ?: return
        dataStore.edit { prefs ->
            prefs[key(owner)] = encode(addRecent(decode(prefs[key(owner)]), chatId))
        }
    }

    private fun key(owner: String) = stringPreferencesKey("$owner:recent_chats")

    @OptIn(ExperimentalStdlibApi::class)
    private fun encode(ids: List<ChatId>): String = ids.joinToString(",") { it.bytes.toHexString() }

    private fun decode(raw: String?): List<ChatId> =
        raw?.split(',')?.filter { it.isNotBlank() }?.mapNotNull { runCatching { ChatId(it) }.getOrNull() }
            .orEmpty()
}

internal const val RECENT_LIMIT = 10

/** [recents] with [chatId] moved to the front, capped at [RECENT_LIMIT]. */
internal fun addRecent(recents: List<ChatId>, chatId: ChatId): List<ChatId> =
    (listOf(chatId) + recents.filterNot { it == chatId }).take(RECENT_LIMIT)
