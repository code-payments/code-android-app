package com.flipcash.shared.chat

/** The chip selected above the Chats list. Not persisted: every cold start opens on [All]. */
enum class ChatListFilter { All, Unread, Groups }

/**
 * One chat as the list projection sees it. [id] is opaque here (the app passes the chat id's
 * `toString()`, its base58 form; the fixture passes its own ids). [unread] is the chat's unread count, with null meaning "unread
 * by an unknown count", which counts as unread.
 */
data class ChatListEntry(
    val id: String,
    val isGroup: Boolean,
    val lastActivityMs: Long,
    val archived: Boolean,
    val muted: Boolean,
    val hidden: Boolean,
    val unread: Int?,
) {
    val isUnread: Boolean get() = unread != 0
}

/**
 * What the Chats list shows, derived from every chat the viewer has.
 *
 * Every id list is ordered by last activity, newest first. Hidden chats (blocked) appear nowhere,
 * including [archived]; their archive record stays in storage in case they are unblocked.
 *
 * @property main every chat that is neither archived nor hidden: the "All" list.
 * @property unreadChip [main] filtered to unread chats, unknown counts included.
 * @property groupsChip [main] filtered to groups. The chip's *number* is [groupsChipCount], the
 *   unread groups among them.
 * @property archived the chats under the Archived row, muted ones included.
 * @property archivedRowVisible whether any chat is archived; the row is shown whenever it is, even
 *   when [main] is empty.
 * @property archivedRowCount unread archived chats that are not muted. Muted archived chats count
 *   toward nothing, as muted chats count toward nothing there today.
 * @property tabBadge unread chats in [main], muted ones included, as the Chats tab badge counts
 *   today. No Android screen reads it: the badge comes from
 *   [observeUnreadChatListCount], which counts the same chats. It stays because the shared
 *   fixture's `list` cases check it on both platforms, and iOS draws its badge from it.
 */
data class ChatListProjection(
    val main: List<String>,
    val unreadChip: List<String>,
    val groupsChip: List<String>,
    val archived: List<String>,
    val unreadChipCount: Int,
    val groupsChipCount: Int,
    val archivedRowVisible: Boolean,
    val archivedRowCount: Int,
    val tabBadge: Int,
) {
    fun idsFor(filter: ChatListFilter): List<String> = when (filter) {
        ChatListFilter.All -> main
        ChatListFilter.Unread -> unreadChip
        ChatListFilter.Groups -> groupsChip
    }

    companion object {
        val Empty = ChatListProjection(
            main = emptyList(),
            unreadChip = emptyList(),
            groupsChip = emptyList(),
            archived = emptyList(),
            unreadChipCount = 0,
            groupsChipCount = 0,
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
 * Ties in last activity keep their input order (`sortedByDescending` is stable).
 */
fun projectChatList(entries: List<ChatListEntry>): ChatListProjection {
    val (archived, active) = entries.filterNot { it.hidden }.partition { it.archived }
    val main = active.sortedByDescending { it.lastActivityMs }
    val archivedSorted = archived.sortedByDescending { it.lastActivityMs }
    val unread = main.filter { it.isUnread }
    val groups = main.filter { it.isGroup }

    return ChatListProjection(
        main = main.map { it.id },
        unreadChip = unread.map { it.id },
        groupsChip = groups.map { it.id },
        archived = archivedSorted.map { it.id },
        unreadChipCount = unread.size,
        groupsChipCount = groups.count { it.isUnread },
        archivedRowVisible = archivedSorted.isNotEmpty(),
        archivedRowCount = archivedSorted.count { it.isUnread && !it.muted },
        tabBadge = unread.size,
    )
}
