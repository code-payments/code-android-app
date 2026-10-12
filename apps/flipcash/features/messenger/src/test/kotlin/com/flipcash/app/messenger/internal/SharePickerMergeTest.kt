package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.core.bytes
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SharePickerMergeTest {

    private fun List<String>.merge(previous: List<String>?) = mergeStable(previous) { it.first() }

    @Test
    fun `no previous list is taken as is`() {
        assertEquals(listOf("b1", "a1"), listOf("b1", "a1").merge(null))
    }

    @Test
    fun `rows keep their old positions and take new contents`() {
        assertEquals(listOf("b2", "a2"), listOf("a2", "b2").merge(listOf("b1", "a1")))
    }

    @Test
    fun `gone rows are dropped and new rows are appended in order`() {
        assertEquals(listOf("b1", "c1", "d1"), listOf("c1", "b1", "d1").merge(listOf("a1", "b1")))
    }

    private fun group(title: String) =
        ConversationReference(chatId = ChatId(UUID.randomUUID().bytes), title = title, isGroup = true)

    @Test
    fun `blank query keeps every chat`() {
        val chats = listOf(group("Alpha"), group("Beta"))
        assertEquals(chats, chats.filterByQuery("  "))
    }

    @Test
    fun `query matches names ignoring case and surrounding space`() {
        val alpha = group("Alpha Club")
        val chats = listOf(alpha, group("Beta"))
        assertEquals(listOf(alpha), chats.filterByQuery(" alPHA "))
        assertEquals(emptyList(), chats.filterByQuery("zzz"))
    }
}
