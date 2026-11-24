package com.flipcash.shared.chat

/** The chip selected above the Chats list. Not persisted: every cold start opens on [All]. */
enum class ChatListFilter { All, Unread, DMs }

/**
 * One chat as the list projection sees it. [id] is opaque here (the app passes the chat id's
 * `toString()`, its base58 form; the fixture passes its own ids). [unread] is the chat's unread count, with null meaning "unread
 * by an unknown count", which counts as unread.
 *
 * [pendingRequests] is how many people wait to join a private group the viewer hosts, and
 * [newestRequestMs] when the newest of them asked. Zero for every other chat. Requests read as
 * unread, and a new one moves the row up the way a new message would.
 */
data class ChatListEntry(
    val id: String,
    val isGroup: Boolean,
    val lastActivityMs: Long,
    val archived: Boolean,
    val muted: Boolean,
    val hidden: Boolean,
    val unread: Int?,
    val pendingRequests: Int = 0,
    val newestRequestMs: Long = 0L,
) {
    val isUnread: Boolean get() = unread != 0 || pendingRequests > 0

    /** What the list sorts by: the later of the last activity and the newest join request. */
    val sortTimeMs: Long get() = maxOf(lastActivityMs, newestRequestMs)
}

/**
 * What the Chats list shows, derived from every chat the viewer has.
 *
 * Every id list is ordered by [ChatListEntry.sortTimeMs], newest first. Hidden chats (blocked) appear nowhere,
 * including [archived]; their archive record stays in storage in case they are unblocked.
 *
 * @property main every chat that is neither archived nor hidden: the "All" list.
 * @property unreadChip [main] filtered to unread chats, unknown counts and pending join requests
 *   included.
 * @property dmsChip [main] filtered to every chat that is not a group. The chip's *number* is
 *   [dmsChipCount], the unread ones among them.
 * @property archived the chats under the Archived row, muted ones included.
 * @property archivedRowVisible whether any chat is archived; the row is shown whenever it is, even
 *   when [main] is empty.
 * @property archivedRowCount unread archived chats, muted ones included. It counts chats, not
 *   messages.
 * @property tabBadge unread chats in [main], muted ones included, as the Chats tab badge counts
 *   today. No Android screen reads it: the badge comes from
 *   [observeUnreadChatListCount], which counts the same chats, requests included. It stays because the shared
 *   fixture's `list` cases check it on both platforms, and iOS draws its badge from it.
 */
data class ChatListProjection(
    val main: List<String>,
    val unreadChip: List<String>,
    val dmsChip: List<String>,
    val archived: List<String>,
    val unreadChipCount: Int,
    val dmsChipCount: Int,
    val archivedRowVisible: Boolean,
    val archivedRowCount: Int,
    val tabBadge: Int,
) {
    fun idsFor(filter: ChatListFilter): List<String> = when (filter) {
        ChatListFilter.All -> main
        ChatListFilter.Unread -> unreadChip
        ChatListFilter.DMs -> dmsChip
    }

    companion object {
        val Empty = ChatListProjection(
            main = emptyList(),
            unreadChip = emptyList(),
            dmsChip = emptyList(),
            archived = emptyList(),
            unreadChipCount = 0,
            dmsChipCount = 0,
            archivedRowVisible = false,
            archivedRowCount = 0,
            tabBadge = 0,
        )
    }
}

/**
 * Splits chats into the main list and the Archived row (an archived chat leaves the list, every
 * chip and the tab badge together), and counts the numbers the list draws. Pure, so the fixture's `list` cases drive it directly.
 *
 * Ties in sort time keep their input order (`sortedByDescending` is stable).
 */
fun projectChatList(entries: List<ChatListEntry>): ChatListProjection {
    val (archived, active) = entries.filterNot { it.hidden }.partition { it.archived }
    val main = active.sortedByDescending { it.sortTimeMs }
    val archivedSorted = archived.sortedByDescending { it.sortTimeMs }
    val unread = main.filter { it.isUnread }
    val dms = main.filterNot { it.isGroup }

    return ChatListProjection(
        main = main.map { it.id },
        unreadChip = unread.map { it.id },
        dmsChip = dms.map { it.id },
        archived = archivedSorted.map { it.id },
        unreadChipCount = unread.size,
        dmsChipCount = dms.count { it.isUnread },
        archivedRowVisible = archivedSorted.isNotEmpty(),
        archivedRowCount = archivedSorted.count { it.isUnread },
        tabBadge = unread.size,
    )
}
