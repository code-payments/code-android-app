package com.flipcash.app.core.extensions

import org.junit.Test
import kotlin.test.assertEquals

class ListExtensionsTest {

    @Test
    fun `filterIsNotInstance removes instances of specified type`() {
        val list: List<Any> = listOf("hello", 1, "world", 2, 3)

        val result = list.filterIsNotInstance<String, Any>()

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `filterIsNotInstance keeps non-matching types`() {
        val list: List<Any> = listOf(1, 2, 3)

        val result = list.filterIsNotInstance<String, Any>()

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `filterIsNotInstance on empty list returns empty`() {
        val list = emptyList<Any>()

        val result = list.filterIsNotInstance<String, Any>()

        assertEquals(emptyList(), result)
    }
}
