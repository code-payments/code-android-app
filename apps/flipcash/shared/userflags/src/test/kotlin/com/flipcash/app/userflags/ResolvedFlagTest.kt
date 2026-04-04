package com.flipcash.app.userflags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolvedFlagTest {

    @Test
    fun `effectiveValue returns serverValue when override is None`() {
        val flag = ResolvedFlag(serverValue = "server", override = FieldOverride.None)
        assertEquals("server", flag.effectiveValue)
    }

    @Test
    fun `effectiveValue returns override value when override is Value`() {
        val flag = ResolvedFlag(serverValue = "server", override = FieldOverride.Value("custom"))
        assertEquals("custom", flag.effectiveValue)
    }

    @Test
    fun `isOverridden is false when override is None`() {
        val flag = ResolvedFlag(serverValue = 42, override = FieldOverride.None)
        assertFalse(flag.isOverridden)
    }

    @Test
    fun `isOverridden is true when override is Value`() {
        val flag = ResolvedFlag(serverValue = 42, override = FieldOverride.Value(99))
        assertTrue(flag.isOverridden)
    }

    @Test
    fun `works with Boolean type`() {
        val flag = ResolvedFlag(serverValue = false, override = FieldOverride.Value(true))
        assertEquals(true, flag.effectiveValue)
        assertTrue(flag.isOverridden)
    }

    @Test
    fun `works with Int type`() {
        val flag = ResolvedFlag(serverValue = 1, override = FieldOverride.None)
        assertEquals(1, flag.effectiveValue)
        assertFalse(flag.isOverridden)
    }

    @Test
    fun `works with nullable type and null serverValue`() {
        val flag = ResolvedFlag<String?>(serverValue = null, override = FieldOverride.None)
        assertEquals(null, flag.effectiveValue)
        assertFalse(flag.isOverridden)
    }

    @Test
    fun `works with nullable type and override replaces null`() {
        val flag = ResolvedFlag<String?>(serverValue = null, override = FieldOverride.Value("value"))
        assertEquals("value", flag.effectiveValue)
        assertTrue(flag.isOverridden)
    }

    @Test
    fun `works with nullable type and override sets null`() {
        val flag = ResolvedFlag<String?>(serverValue = "original", override = FieldOverride.Value(null))
        assertEquals(null, flag.effectiveValue)
        assertTrue(flag.isOverridden)
    }
}
