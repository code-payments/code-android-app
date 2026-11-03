package com.flipcash.shared.chat.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.flipcash.app.core.storage.PreferencesFiles
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.LobbyRequestStore
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * One string set per account, each entry `<chat id hex>:<entered_at epoch seconds>`. Per account
 * because one device serves several, and a request belongs to the account that sent it.
 */
@Singleton
internal class DataStoreLobbyRequestStore @Inject constructor(
    preferencesFiles: PreferencesFiles,
    private val userManager: UserManager,
) : LobbyRequestStore {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { preferencesFiles.file(DATASTORE_NAME) },
    )

    override fun observe(): Flow<Map<ChatId, Instant>> =
        combine(
            userManager.state.map { it.accountId?.hexEncodedString() }.distinctUntilChanged(),
            dataStore.data,
        ) { owner, prefs ->
            owner ?: return@combine emptyMap()
            prefs[key(owner)].orEmpty().mapNotNull(::decode).toMap()
        }.distinctUntilChanged()

    override suspend fun put(chatId: ChatId, enteredAt: Instant) = edit { entries ->
        entries.filterKeys { it != chatId } + (chatId to enteredAt)
    }

    override suspend fun remove(chatId: ChatId) = edit { entries -> entries - chatId }

    private suspend fun edit(change: (Map<ChatId, Instant>) -> Map<ChatId, Instant>) {
        val owner = userManager.accountId?.hexEncodedString() ?: return
        dataStore.edit { prefs ->
            val key = key(owner)
            val current = prefs[key].orEmpty().mapNotNull(::decode).toMap()
            prefs[key] = change(current).map { (chatId, at) -> encode(chatId, at) }.toSet()
        }
    }

    private fun key(owner: String) = stringSetPreferencesKey("requests-$owner")

    @OptIn(ExperimentalStdlibApi::class)
    private fun encode(chatId: ChatId, enteredAt: Instant) = "${chatId.bytes.toHexString()}:${enteredAt.epochSeconds}"

    @OptIn(ExperimentalStdlibApi::class)
    private fun decode(raw: String): Pair<ChatId, Instant>? {
        val separator = raw.indexOf(':').takeIf { it > 0 } ?: return null
        val bytes = runCatching { raw.substring(0, separator).hexToByteArray() }.getOrNull() ?: return null
        val seconds = raw.substring(separator + 1).toLongOrNull() ?: return null
        return ChatId(bytes) to Instant.fromEpochSeconds(seconds)
    }

    private companion object {
        const val DATASTORE_NAME = "lobby_requests"
    }
}
