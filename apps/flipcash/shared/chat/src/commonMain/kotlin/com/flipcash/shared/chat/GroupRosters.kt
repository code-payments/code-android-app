package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.RosterChange
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Where the event stream hands a group's roster changes, beside the list-level handling. */
fun interface RosterUpdateSink {
    suspend fun apply(chatId: ChatId, changes: List<RosterChange>)

    /** Drops everything held. For logout: the rosters belong to the account. */
    fun clear() = Unit

    object None : RosterUpdateSink {
        override suspend fun apply(chatId: ChatId, changes: List<RosterChange>) = Unit
    }
}

/**
 * Full rosters of the groups whose members are shown in full (a private group's Members grid and
 * list), most recently joined first.
 *
 * [load] reads a roster to the end; the event stream's RosterUpdates are applied on top through
 * [apply]. A join is kept per member by [ChatMember.version], the greater wins, as [RosterPage]
 * documents for merging pages.
 */
@Singleton
class GroupRosters @Inject constructor(
    private val chatController: ChatController,
) : RosterUpdateSink {

    private val rosters = MutableStateFlow<Map<ChatId, List<ChatMember>>>(emptyMap())

    /** Each loaded roster, most recently joined first. A chat never loaded is absent. */
    val state: StateFlow<Map<ChatId, List<ChatMember>>> = rosters.asStateFlow()

    /** Fetches [chatId]'s whole roster, every page, and replaces what was held for it. */
    suspend fun load(chatId: ChatId): Result<List<ChatMember>> = runCatching {
        val members = mutableListOf<ChatMember>()
        var options = QueryOptions()
        do {
            val page = chatController.getRoster(chatId, options).getOrThrow()
            members += page.members
            options = options.copy(token = page.pagingToken)
        } while (page.hasMore && page.pagingToken != null)
        newestFirst(members.fold(emptyList(), ::merge))
    }.onSuccess { members ->
        rosters.update { it + (chatId to members) }
    }

    /**
     * Applies changes only to a roster already loaded: a partial one would read as complete.
     *
     * A removal reaches the members who stay as [RosterChange.MembershipChanged], which names no one
     * (see its contract), so it moves the count and cannot say who to drop. The held list is then
     * read again, which is what keeps it matching the count.
     */
    override suspend fun apply(chatId: ChatId, changes: List<RosterChange>) {
        applyEdits(chatId, changes)
        if (chatId in rosters.value && changes.any { it is RosterChange.MembershipChanged }) {
            load(chatId)
        }
    }

    /**
     * Drops [userId] from a loaded roster now. The host's own Remove uses it so the list does not
     * wait on the event that follows; that event's reread then confirms it.
     */
    fun remove(chatId: ChatId, userId: ID) {
        rosters.update { all ->
            val current = all[chatId] ?: return@update all
            all + (chatId to current.filterNot { it.userId == userId })
        }
    }

    private fun applyEdits(chatId: ChatId, changes: List<RosterChange>) {
        rosters.update { all ->
            val current = all[chatId] ?: return@update all
            val next = changes.fold(current) { members, change ->
                when (change) {
                    is RosterChange.MemberJoined -> merge(members, change.member)
                    is RosterChange.MemberLeft -> members.filterNot { it.userId == change.userId }
                    is RosterChange.MembershipChanged -> members
                }
            }
            all + (chatId to newestFirst(next))
        }
    }

    override fun clear() {
        rosters.value = emptyMap()
    }

    private fun merge(members: List<ChatMember>, member: ChatMember): List<ChatMember> {
        val held = members.firstOrNull { it.userId == member.userId }
        return when {
            held == null -> members + member
            member.version > held.version -> members.map { if (it.userId == member.userId) member else it }
            else -> members
        }
    }

    private fun newestFirst(members: List<ChatMember>) =
        members.sortedByDescending { it.joinedAt }
}
