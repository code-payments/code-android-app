package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The chat-list read rules and the actions that apply them. Numbers are the spec's test cases. */
class ChatReadActionsTest {

    private class FakeMarks(initial: Set<ChatId> = emptySet()) : ChatMarkedUnreadStore {
        val marked = MutableStateFlow(initial)
        override fun observeMarked(): Flow<Set<ChatId>> = marked
        override suspend fun mark(chatId: ChatId) { marked.value += chatId }
        override suspend fun clear(chatId: ChatId) { marked.value -= chatId }
        override suspend fun clear(chatIds: Collection<ChatId>) { marked.value -= chatIds.toSet() }
        override suspend fun clearAll() { marked.value = emptySet() }
    }

    private val a = ChatId("aa00")
    private val b = ChatId("bb00")
    private val c = ChatId("cc00")
    private val e = ChatId("ee00")

    private fun chat(id: ChatId, unread: Int?, marked: Boolean = false) = ChatSummary(
        metadata = ChatMetadata(
            chatId = id,
            type = ChatType.GROUP,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(0),
        ),
        unreadCount = unread,
        markedUnread = marked,
    )

    private val advanced = mutableListOf<ChatId>()
    private fun actions(marks: FakeMarks) = ChatReadActions(marks) { advanced += it }

    // 1
    @Test
    fun `mark as unread on a read chat marks it and sends nothing`() = runTest {
        val marks = FakeMarks()
        assertEquals(ChatReadToggle.MarkUnread, readToggleFor(chat(c, 0).isUnread))
        actions(marks).markUnread(c)
        assertEquals(setOf(c), marks.marked.value)
        assertEquals(emptyList(), advanced)
    }

    // 2
    @Test
    fun `mark as read on a marked chat with nothing unread clears the mark and sends nothing`() = runTest {
        val marks = FakeMarks(setOf(b))
        val chat = chat(b, 0, marked = true)
        assertEquals(ChatReadToggle.MarkRead, readToggleFor(chat.isUnread))
        actions(marks).markRead(chat)
        assertEquals(emptySet(), marks.marked.value)
        assertEquals(emptyList(), advanced)
    }

    // 3
    @Test
    fun `a chat with unread messages offers mark as read, which advances READ`() = runTest {
        val chat = chat(a, 3)
        assertEquals(ChatReadToggle.MarkRead, readToggleFor(chat.isUnread))
        actions(FakeMarks()).markRead(chat)
        assertEquals(listOf(a), advanced)
    }

    @Test
    fun `an unknown unread count reads as unread and advances`() = runTest {
        val chat = chat(a, null)
        assertTrue(chat.isUnread)
        actions(FakeMarks()).markRead(chat)
        assertEquals(listOf(a), advanced)
    }

    // 9
    @Test
    fun `read all advances unread, clears marks, leaves read chats and archived marks`() = runTest {
        val d = ChatId("dd00")
        val marks = FakeMarks(setOf(b, d))
        val main = listOf(chat(a, 2), chat(b, 0, marked = true), chat(c, 0))

        val plan = actions(marks).readAll(main)

        assertEquals(listOf(a), advanced)
        assertEquals(setOf(d), marks.marked.value)
        assertEquals(2, plan.count)
    }

    @Test
    fun `read all counts a chat that is both unread and marked once`() {
        assertEquals(1, readAllPlan(listOf(chat(a, 2, marked = true))).count)
    }

    // 10
    @Test
    fun `read all has nothing to do when nothing in the main list is unread`() {
        assertTrue(readAllPlan(listOf(chat(c, 0), chat(e, 0))).isEmpty)
        assertFalse(readAllPlan(listOf(chat(c, 0), chat(b, 0, marked = true))).isEmpty)
    }

    // 12
    @Test
    fun `selection with an unread chat offers mark as read and reads both`() = runTest {
        val marks = FakeMarks()
        val toggle = actions(marks).toggle(listOf(chat(a, 1), chat(c, 0)))
        assertEquals(ChatReadToggle.MarkRead, toggle)
        assertEquals(listOf(a), advanced)
        assertEquals(emptySet(), marks.marked.value)
    }

    @Test
    fun `selection mark as read clears a selected mark too`() = runTest {
        val marks = FakeMarks(setOf(b))
        actions(marks).toggle(listOf(chat(b, 0, marked = true), chat(c, 0)))
        assertEquals(emptySet(), marks.marked.value)
    }

    // 13
    @Test
    fun `selection of read chats offers mark as unread and marks both`() = runTest {
        val marks = FakeMarks()
        val toggle = actions(marks).toggle(listOf(chat(c, 0), chat(e, 0)))
        assertEquals(ChatReadToggle.MarkUnread, toggle)
        assertEquals(setOf(c, e), marks.marked.value)
        assertEquals(emptyList(), advanced)
    }

    // 14 (the sheet and the duration are the screen's; this is the label)
    @Test
    fun `a mixed muted selection offers mute`() {
        assertEquals(ChatMuteToggle.Mute, bulkMuteToggle(listOf(true, false)))
        assertEquals(ChatMuteToggle.Mute, bulkMuteToggle(emptyList()))
    }

    // 15
    @Test
    fun `an all-muted selection offers unmute`() {
        assertEquals(ChatMuteToggle.Unmute, bulkMuteToggle(listOf(true, true)))
    }

    // 16 (the label count)
    @Test
    fun `the archive toast count is the number archived`() {
        assertEquals(2, archivedToastCount(2))
        assertEquals(null, archivedToastCount(0))
    }
}
