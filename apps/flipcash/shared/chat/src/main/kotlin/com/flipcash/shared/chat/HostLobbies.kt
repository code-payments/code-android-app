package com.flipcash.shared.chat

import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.AdmitLobbyMemberError
import com.flipcash.services.models.DenyLobbyMemberError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.services.models.chat.LobbyUpdate
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Where the event stream hands a private group's lobby changes. */
fun interface LobbyUpdateSink {
    fun apply(chatId: ChatId, updates: List<LobbyUpdate>)

    /** Drops everything held. For logout: the lobbies belong to the account. */
    fun clear() = Unit

    object None : LobbyUpdateSink {
        override fun apply(chatId: ChatId, updates: List<LobbyUpdate>) = Unit
    }
}

/**
 * The lobbies of the private groups this account created: who is waiting to join each, newest
 * first. One copy for every host surface (profile card, chat-list row, Join Requests, in-chat
 * anchor) so their counts agree.
 *
 * [load] fetches a lobby whole; the event stream's LobbyUpdates are applied on top through
 * [apply]. Those updates are unversioned and best-effort, so a surface that needs the lobby to be
 * right calls [load] again.
 */
@Singleton
class HostLobbies @Inject constructor(
    private val chatController: ChatController,
    private val keyEnvelopes: GroupKeyEnvelopes,
) : LobbyUpdateSink {

    private val lobbies = MutableStateFlow<Map<ChatId, List<LobbyMember>>>(emptyMap())

    /** Each loaded lobby's waiting members, newest first. A chat never loaded is absent. */
    val state: StateFlow<Map<ChatId, List<LobbyMember>>> = lobbies.asStateFlow()

    /** Fetches [chatId]'s whole lobby, every page, and replaces what was held for it. */
    suspend fun load(chatId: ChatId): Result<List<LobbyMember>> = runCatching {
        val members = mutableListOf<LobbyMember>()
        var options = QueryOptions()
        do {
            val page = chatController.getLobbyMembers(chatId, options).getOrThrow()
            members += page.members
            options = options.copy(token = page.pagingToken)
        } while (page.hasMore && page.pagingToken != null)
        newestFirst(members.distinctBy { it.userProfile.userId })
    }.onSuccess { members ->
        lobbies.update { it + (chatId to members) }
    }

    override fun apply(chatId: ChatId, updates: List<LobbyUpdate>) {
        lobbies.update { all ->
            val current = all[chatId].orEmpty()
            val next = updates.fold(current) { members, update ->
                when (update) {
                    is LobbyUpdate.MemberEntered ->
                        members.filterNot { it.userProfile.userId == update.member.userProfile.userId } +
                            update.member
                    is LobbyUpdate.MemberLeft ->
                        members.filterNot { it.userProfile.userId == update.userId }
                }
            }
            all + (chatId to newestFirst(next))
        }
    }

    /**
     * Wraps the chat key for [member] and admits them. A member who already left the lobby
     * (NOT_IN_LOBBY) is dropped from the list and counts as done: there is nothing left to act on.
     */
    suspend fun approve(chatId: ChatId, member: LobbyMember): Result<Unit> {
        val userId = member.userProfile.userId
            ?: return Result.failure(IllegalArgumentException("Lobby member has no user id"))
        val result = keyEnvelopes.wrapFor(chatId, member.publicKey.byteArray)
            .mapCatching { envelope ->
                chatController.admitLobbyMember(chatId, userId, envelope).getOrThrow()
            }
            .recoverCatching { if (it !is AdmitLobbyMemberError.NotInLobby) throw it }
        if (result.isSuccess) remove(chatId, userId)
        return result
    }

    /** Turns [userId] away. One already gone from the lobby (NOT_FOUND) counts as done. */
    suspend fun deny(chatId: ChatId, userId: ID): Result<Unit> {
        val result = chatController.denyLobbyMember(chatId, userId)
            .recoverCatching { if (it !is DenyLobbyMemberError.NotFound) throw it }
        if (result.isSuccess) remove(chatId, userId)
        return result
    }

    override fun clear() {
        lobbies.value = emptyMap()
    }

    private fun remove(chatId: ChatId, userId: ID) {
        lobbies.update { all ->
            val current = all[chatId] ?: return@update all
            all + (chatId to current.filterNot { it.userProfile.userId == userId })
        }
    }

    private fun newestFirst(members: List<LobbyMember>) = members.sortedByDescending { it.enteredAt }
}
