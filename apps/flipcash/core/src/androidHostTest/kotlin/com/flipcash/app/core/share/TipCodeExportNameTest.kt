package com.flipcash.app.core.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TipCodeExportNameTest {

    @Test
    fun `an ordinary name passes through`() {
        assertEquals("Chat with Brandon", exportFileBaseName("Chat with Brandon"))
    }

    @Test
    fun `characters a file system rejects are dropped`() {
        assertEquals("Chat with ab", exportFileBaseName("Chat with a/b:*?\"<>|\\"))
    }

    @Test
    fun `control characters and surrounding space are dropped`() {
        assertEquals("Chat with Sam", exportFileBaseName("  Chat with Sam\n\t "))
    }

    @Test
    fun `a name that sanitizes to nothing is null so the caller falls back`() {
        assertNull(exportFileBaseName("  /:*  "))
        assertNull(exportFileBaseName(""))
    }

    @Test
    fun `a very long name is capped`() {
        assertEquals(80, exportFileBaseName("a".repeat(200))!!.length)
    }

    @Test
    fun `emoji are kept whole when the cap lands inside one`() {
        val name = "a".repeat(79) + "😀"
        assertEquals("a".repeat(79), exportFileBaseName(name))
    }
}
