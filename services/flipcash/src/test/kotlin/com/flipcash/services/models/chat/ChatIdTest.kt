package com.flipcash.services.models.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    fun `toString includes byte size`() {
        val id = ChatId(ByteArray(32))
        assertTrue(id.toString().contains("32 bytes"))
    }

    @Test
    fun `equals returns false for non-ChatId`() {
        val id = ChatId(byteArrayOf(1, 2, 3))
        @Suppress("AssertBetweenInconvertibleTypes")
        assertNotEquals<Any>(id, "not a ChatId")
    }
}
