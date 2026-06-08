package com.flipcash.services.models.chat

import com.getcode.utils.base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ChatIdTest {

    @Test
    fun `equals returns true for same byte content`() {
        val a = ChatId(byteArrayOf(1, 2, 3))
        val b = ChatId(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
    }

    @Test
    fun `equals returns false for different byte content`() {
        val a = ChatId(byteArrayOf(1, 2, 3))
        val b = ChatId(byteArrayOf(4, 5, 6))
        assertNotEquals(a, b)
    }

    @Test
    fun `hashCode is consistent for equal instances`() {
        val a = ChatId(byteArrayOf(10, 20, 30))
        val b = ChatId(byteArrayOf(10, 20, 30))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `toString returns base58 encoding`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val id = ChatId(bytes)
        assertEquals(bytes.base58, id.toString())
    }

    @Test
    fun `equals returns false for non-ChatId`() {
        val id = ChatId(byteArrayOf(1, 2, 3))
        @Suppress("AssertBetweenInconvertibleTypes")
        assertNotEquals<Any>(id, "not a ChatId")
    }
}
