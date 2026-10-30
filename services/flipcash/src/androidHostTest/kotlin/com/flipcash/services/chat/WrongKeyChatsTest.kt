package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WrongKeyChatsTest {

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val other = ChatId(ByteArray(32) { 8 })
    private val chats = WrongKeyChats()

    @Test
    fun `a marked chat is observed until it is cleared`() = runTest {
        assertFalse(chats.observe(chatId).first())

        chats.mark(chatId)
        assertTrue(chats.observe(chatId).first())
        assertFalse(chats.observe(other).first())

        chats.clear(chatId)
        assertFalse(chats.observe(chatId).first())
    }

    @Test
    fun `marking twice keeps one entry`() {
        chats.mark(chatId)
        chats.mark(ChatId(ByteArray(32) { 7 }))

        assertEquals(setOf(chatId), chats.chats.value)
    }
}
