package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId

/** The one read action a chat offers in the slot its swipe and select mode share. */
enum class ChatReadToggle { MarkRead, MarkUnread }

/** Mark as Read when the chat is unread (messages or a mark), Mark as Unread otherwise. */
fun readToggleFor(isUnread: Boolean): ChatReadToggle =
    if (isUnread) ChatReadToggle.MarkRead else ChatReadToggle.MarkUnread

/**
 * Select mode's read action: Mark as Read when any selected chat is unread, applied to every
 * selected chat; otherwise Mark as Unread, applied to every selected chat.
 */
fun bulkReadToggle(selectedUnread: Iterable<Boolean>): ChatReadToggle =
    readToggleFor(selectedUnread.any { it })

/** Select mode's mute action. */
enum class ChatMuteToggle { Mute, Unmute }

/**
 * Unmute only when every selected chat is muted. Otherwise Mute, which opens the mute sheet once
 * and applies its duration to every selected chat, ones already muted included.
 */
fun bulkMuteToggle(selectedMuted: Collection<Boolean>): ChatMuteToggle =
    if (selectedMuted.isNotEmpty() && selectedMuted.all { it }) ChatMuteToggle.Unmute else ChatMuteToggle.Mute

/**
 * What Mark as Read does to one chat. [advance]: it has unread messages, so READ moves to the
 * latest message, the same call opening the chat makes. [clearMark]: it is marked. A marked chat
 * with nothing unread sends nothing.
 */
data class ChatReadChange(val advance: Boolean, val clearMark: Boolean)

fun ChatSummary.markReadChange(): ChatReadChange =
    ChatReadChange(advance = unreadCount != 0, clearMark = markedUnread)

/**
 * What Read All does to the main list: advance READ on every chat with unread messages (muted
 * ones included) and clear every mark. Archived chats are not in the main list, so their marks stay.
 *
 * Read All is enabled exactly when this plan is not empty, so the menu and the confirm agree.
 *
 * @property count the chats whose READ advanced or mark cleared, each counted once: the `Count`
 *   on Chats Read All.
 */
data class ChatReadAllPlan(val advance: List<ChatId>, val clearMarks: List<ChatId>) {
    val count: Int get() = (advance + clearMarks).toSet().size
    val isEmpty: Boolean get() = advance.isEmpty() && clearMarks.isEmpty()
}

fun readAllPlan(main: List<ChatSummary>): ChatReadAllPlan {
    val changes = main.map { it.metadata.chatId to it.markReadChange() }
    return ChatReadAllPlan(
        advance = changes.filter { it.second.advance }.map { it.first },
        clearMarks = changes.filter { it.second.clearMark }.map { it.first },
    )
}

/**
 * The count the bulk archive toast names: "Chat archived" for one, "%d chats archived" for more.
 * Null when nothing was archived, so no toast is shown.
 */
fun archivedToastCount(archived: Int): Int? = archived.takeIf { it > 0 }
