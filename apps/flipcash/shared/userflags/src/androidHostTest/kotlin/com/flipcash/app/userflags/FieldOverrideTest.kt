package com.flipcash.app.userflags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class FieldOverrideTest {

    @Test
    fun `None is a singleton`() {
        assertSame(FieldOverride.None, FieldOverride.None)
    }

    @Test
    fun `None is a FieldOverride`() {
        assertIs<FieldOverride<Nothing>>(FieldOverride.None)
    }

    @Test
    fun `Value wraps its value`() {
        val override = FieldOverride.Value(42)
        assertEquals(42, override.value)
    }

    @Test
    fun `Value wraps string`() {
        val override = FieldOverride.Value("hello")
        assertEquals("hello", override.value)
    }

    @Test
    fun `Value wraps null`() {
        val override = FieldOverride.Value<String?>(null)
        assertEquals(null, override.value)
    }

    @Test
    fun `Value is a FieldOverride`() {
        val override: FieldOverride<Int> = FieldOverride.Value(10)
        assertIs<FieldOverride.Value<Int>>(override)
    }

    @Test
    fun `Value equality based on wrapped value`() {
        assertEquals(FieldOverride.Value(5), FieldOverride.Value(5))
    }

    @Test
    fun `Value copy preserves semantics`() {
        val original = FieldOverride.Value(100)
        val copy = original.copy(value = 200)
        assertEquals(200, copy.value)
    }
}
