package com.flipcash.app.tipping.internal

import com.flipcash.analytics.ChatReadSource
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatReadActions
import com.flipcash.shared.chat.ChatReadAllPlan
import com.flipcash.shared.chat.ChatReadToggle
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.currentChatListFeeds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What a chat list row, select mode and the "…" menu do, shared by the Chats and Archived lists:
 * the read actions through [ChatReadActions], archive, bulk unmute, and the analytics each sends.
 *
 * Chats are looked up in the coordinator's feeds rather than taken from the rows, so a decision
 * (unread or not, muted or not) reads the same state the rows were drawn from.
 */
class ChatRowActions @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val readActions: ChatReadActions,
    private val archiveStore: ChatArchiveStore,
    private val analytics: FlipcashAnalytics,
) {

    /** The main list's and the archived list's summaries for [ids], in [ids] order. */
    fun summaries(ids: Collection<ChatId>): List<ChatSummary> {
        val feeds = chatCoordinator.currentChatListFeeds()
        val all = (feeds?.main.orEmpty() + feeds?.archived.orEmpty()).associateBy { it.metadata.chatId }
        return ids.mapNotNull { all[it] }
    }

    /** The main list, as Read All sees it. */
    fun mainList(): List<ChatSummary> = chatCoordinator.currentChatListFeeds()?.main.orEmpty()

    /** The leading swipe: Mark as Read on an unread chat, Mark as Unread otherwise. */
    suspend fun toggleRead(chatId: ChatId, source: ChatReadSource) {
        val chat = summaries(listOf(chatId)).firstOrNull() ?: return
        if (chat.isUnread) {
            readActions.markRead(chat)
            analytics.track(ChatEvents.markedRead(chatType = chat.metadata.type.analytics, source = source))
        } else {
            readActions.markUnread(chatId)
            analytics.track(ChatEvents.markedUnread(chatType = chat.metadata.type.analytics, source = source))
        }
    }

    /** Select mode's read toggle over [ids]: one event per selected chat. */
    suspend fun toggleRead(ids: Collection<ChatId>) {
        val chats = summaries(ids)
        val toggle = readActions.toggle(chats)
        chats.forEach { chat ->
            val chatType = chat.metadata.type.analytics
            analytics.track(
                when (toggle) {
                    ChatReadToggle.MarkRead -> ChatEvents.markedRead(chatType = chatType, source = ChatReadSource.SELECTION)
                    ChatReadToggle.MarkUnread -> ChatEvents.markedUnread(chatType = chatType, source = ChatReadSource.SELECTION)
                }
            )
        }
    }

    /** Read All over the main list. */
    suspend fun readAll(): ChatReadAllPlan {
        val plan = readActions.readAll(mainList())
        analytics.track(ChatEvents.readAll(count = plan.count))
        return plan
    }

    /** Select mode's Unmute, offered only when every selected chat is muted. */
    suspend fun unmute(ids: Collection<ChatId>) = coroutineScope {
        summaries(ids).forEach { chat ->
            launch {
                val result = chatCoordinator.unmute(chat.metadata.chatId)
                analytics.track(
                    ChatEvents.unmuted(
                        chatType = chat.metadata.type.analytics,
                        state = if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
                        error = result.exceptionOrNull()?.chatResult,
                    )
                )
            }
        }
    }

    suspend fun archive(ids: Collection<ChatId>) = ids.forEach { archiveStore.archive(it) }

    suspend fun unarchive(ids: Collection<ChatId>) = ids.forEach { archiveStore.unarchive(it) }
}

/** [ids]' chat types, by index, for the bulk mute sheet's per-chat events. */
internal fun ChatRowActions.chatTypes(ids: List<ChatId>): Pair<List<ChatId>, List<ChatType>> {
    val chats = summaries(ids)
    return chats.map { it.metadata.chatId } to chats.map { it.metadata.type }
}
