package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private groups whose invite key bundle failed a check: the member joined, but the copy of the
 * chat key their link carried is not one they can trust. The chat shows the wrong-key state and
 * must not send. The contract's only repair is to leave and be admitted from the lobby
 * (`KeyEnvelope` comment), so nothing here retries.
 *
 * [GroupKeyEnvelopes] writes it; a chat whose key later opens drops out. Held in memory only: the
 * next launch fetches the bundle again and lands here again if it still fails. Logout calls
 * [reset], since chat ids aren't scoped to the account that marked them.
 */
@Singleton
class WrongKeyChats @Inject constructor() {
    private val wrong = MutableStateFlow<Set<ChatId>>(emptySet())

    val chats: StateFlow<Set<ChatId>> = wrong.asStateFlow()

    fun observe(chatId: ChatId): Flow<Boolean> = wrong.map { chatId in it }.distinctUntilChanged()

    internal fun mark(chatId: ChatId) = wrong.update { it + chatId }

    internal fun clear(chatId: ChatId) {
        if (chatId in wrong.value) wrong.update { it - chatId }
    }

    fun reset() {
        wrong.value = emptySet()
    }
}
