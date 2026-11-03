package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mark as Read, Mark as Unread and Read All, for the chat list. Marks go through
 * [ChatMarkedUnreadStore]; READ goes through the coordinator's [ChatCoordinator.markAsRead].
 * Mark as Unread sends nothing.
 */
class ChatReadActions internal constructor(
    private val marks: ChatMarkedUnreadStore,
    private val advanceRead: suspend (ChatId) -> Unit,
) {

    @Inject
    constructor(coordinator: ChatCoordinator, marks: ChatMarkedUnreadStore) :
        this(marks, { chatId -> coordinator.markAsRead(chatId) })

    suspend fun markUnread(chatId: ChatId) = marks.mark(chatId)

    suspend fun markRead(chat: ChatSummary) {
        val change = chat.markReadChange()
        if (change.clearMark) marks.clear(chat.metadata.chatId)
        if (change.advance) advanceRead(chat.metadata.chatId)
    }

    /** Applies the read toggle to [chats]: [ChatReadToggle.MarkRead] if any is unread. */
    suspend fun toggle(chats: List<ChatSummary>): ChatReadToggle {
        val toggle = bulkReadToggle(chats.map { it.isUnread })
        coroutineScope {
            chats.forEach { chat ->
                launch {
                    when (toggle) {
                        ChatReadToggle.MarkRead -> markRead(chat)
                        ChatReadToggle.MarkUnread -> markUnread(chat.metadata.chatId)
                    }
                }
            }
        }
        return toggle
    }

    /** Read All over [main], the main list. Returns what it did. */
    suspend fun readAll(main: List<ChatSummary>): ChatReadAllPlan {
        val plan = readAllPlan(main)
        marks.clear(plan.clearMarks)
        coroutineScope { plan.advance.forEach { launch { advanceRead(it) } } }
        return plan
    }
}
