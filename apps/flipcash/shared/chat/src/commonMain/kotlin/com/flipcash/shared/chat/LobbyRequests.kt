package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.EnterLobbyError
import com.flipcash.services.models.LeaveLobbyError
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * The private groups this account has asked to join and not yet seen an answer for, keyed by chat
 * with the time the request went in.
 *
 * The contract has no "my requests" list and no push for an outcome: an admission or a denial only
 * shows as `GetChat.in_lobby` going false. So the requests are kept on the device, and [refresh]
 * asks GetChat about each one. A request that has left the lobby is dropped; if any did, the feed
 * is synced, since an admitted chat arrives there and its row is what flips the viewer to member.
 */
@Singleton
class LobbyRequests @Inject constructor(
    private val chatController: ChatController,
    private val store: LobbyRequestStore,
    private val chatCoordinator: ChatCoordinator,
) {

    /** Requests sent and not yet answered, re-emitted on every change. */
    fun observe(): Flow<Map<ChatId, Instant>> = store.observe()

    /**
     * EnterLobby. On success the request is kept with the server's `entered_at`. ALREADY_MEMBER
     * drops any stale request and syncs the feed so the member's row arrives; it still fails, so
     * the caller can tell it apart. Other failures change nothing.
     */
    suspend fun request(chatId: ChatId): Result<Instant> =
        chatController.enterLobby(chatId)
            .onSuccess { store.put(chatId, it.enteredAt) }
            .onFailure { error ->
                if (error is EnterLobbyError.AlreadyMember) {
                    store.remove(chatId)
                    chatCoordinator.refreshFeed()
                }
            }
            .map { it.enteredAt }

    /**
     * LeaveLobby. NOT_FOUND means the request is already gone (denied, or admitted and since
     * left), so it also counts as cancelled.
     */
    suspend fun cancel(chatId: ChatId): Result<Unit> =
        chatController.leaveLobby(chatId)
            .recoverCatching { if (it !is LeaveLobbyError.NotFound) throw it }
            .onSuccess { store.remove(chatId) }

    /** Drops the stored request for [chatId], for a viewer who left the lobby by other means. */
    suspend fun clear(chatId: ChatId) = store.remove(chatId)

    /**
     * Asks GetChat about [chatIds], or every stored request when null, and drops each that is no
     * longer in the lobby. A failed GetChat keeps its request: an unanswered question is not a no.
     */
    suspend fun refresh(chatIds: Collection<ChatId>? = null) {
        val stored = store.observe().first()
        val asking = chatIds?.filter { it in stored } ?: stored.keys
        var resolved = false
        asking.forEach { chatId ->
            val metadata = chatController.getChat(chatId).getOrNull() ?: return@forEach
            if (!metadata.inLobby) {
                store.remove(chatId)
                resolved = true
            }
        }
        if (resolved) chatCoordinator.refreshFeed()
    }
}

/** Where [LobbyRequests] keeps its requests, per signed-in account. */
interface LobbyRequestStore {
    /** The current account's requests; empty before sign-in. */
    fun observe(): Flow<Map<ChatId, Instant>>

    suspend fun put(chatId: ChatId, enteredAt: Instant)

    suspend fun remove(chatId: ChatId)
}
