package com.getcode.opencode.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapsTest {

    @Test
    fun getOrPutIfNonNullReturnsExistingValue() {
        val map = mutableMapOf("a" to 1)
        val result = map.getOrPutIfNonNull("a") { 99 }
        assertEquals(1, result)
        assertEquals(1, map["a"])
    }

    @Test
    fun getOrPutIfNonNullInsertsNewValue() {
        val map = mutableMapOf<String, Int>()
        val result = map.getOrPutIfNonNull("a") { 42 }
        assertEquals(42, result)
        assertEquals(42, map["a"])
    }

    @Test
    fun getOrPutIfNonNullSkipsNullDefault() {
        val map = mutableMapOf<String, Int>()
        val result = map.getOrPutIfNonNull("a") { null }
        assertNull(result)
        assertNull(map["a"])
    }

    @Test
    fun getOrPutIfNonNullDoesNotCallDefaultWhenKeyExists() {
        val map = mutableMapOf("a" to 1)
        var called = false
        map.getOrPutIfNonNull("a") {
            called = true
            99
        }
        assertEquals(false, called)
    }
}
