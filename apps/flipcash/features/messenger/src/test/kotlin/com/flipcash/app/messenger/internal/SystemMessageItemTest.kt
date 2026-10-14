package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.isSystemBlank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The shared fixture with iOS: what a system notice becomes in the transcript, and what is blank. */
class SystemMessageItemTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun item(text: String, id: Long = 7, index: Int = 0) =
        systemMessageItem(id, index, at, MessageContent.System(text))

    @Test
    fun `a notice becomes exactly one row with its own text`() {
        val row = assertNotNull(item("Alex joined the group"))
        assertEquals("Alex joined the group", row.text)
        assertEquals("sys-7", row.itemKey)
        assertEquals("system-message", row.itemContentType)
        assertEquals(7L, row.messageId)
        assertEquals(at, row.timestamp)
        assertFalse(row.isFromSelf)
    }

    @Test
    fun `a second content of one message gets a suffixed key`() {
        assertEquals("sys-7-1", assertNotNull(item("Sam left", index = 1)).itemKey)
        assertEquals("sys-7", assertNotNull(item("Sam left", index = 0)).itemKey)
    }

    @Test
    fun `blank text produces no row`() {
        listOf("", "   ", "\n\t", "\u0085", "\u00A0", "\u3000").forEach { blank ->
            assertNull(item(blank), "expected no row for ${blank.map { it.code }}")
        }
    }

    @Test
    fun `the unit separator is not blank`() {
        // Kotlin's isWhitespace() treats U+001C-001F as space and U+0085 as not; iOS is the reverse.
        assertFalse("\u001F".isSystemBlank())
        assertTrue("\u0085".isSystemBlank())
        assertNotNull(item("\u001F"))
    }

    @Test
    fun `text that is not blank is kept unmodified`() {
        assertEquals("  Alex joined \n", assertNotNull(item("  Alex joined \n")).text)
    }

    @Test
    fun `the shared whitespace set is blank and nothing else near it is`() {
        val whitespace = (0x09..0x0D) + 0x20 + 0x85 + 0xA0 + 0x1680 + (0x2000..0x200A) +
            0x2028 + 0x2029 + 0x202F + 0x205F + 0x3000
        whitespace.forEach { assertTrue(it.toChar().toString().isSystemBlank(), "U+%04X".format(it)) }
        listOf(0x08, 0x0E, 0x1C, 0x1F, 0x21, 0x180E, 0x200B, 0x2060, 0xFEFF).forEach {
            assertFalse(it.toChar().toString().isSystemBlank(), "U+%04X".format(it))
        }
    }
}
