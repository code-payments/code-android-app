package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.jumpsToBottom
import com.getcode.ui.components.unreadCountLabel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the jump-to-bottom button says and how a tap travels. */
class JumpToBottomTest {

    @Test
    fun `badge caps at 99+`() {
        assertEquals("99+", unreadCountLabel(150))
        assertEquals("99+", unreadCountLabel(100))
        assertEquals("99", unreadCountLabel(99))
        assertEquals("1", unreadCountLabel(1))
    }

    @Test
    fun `a tap animates up to 100 rows away and jumps beyond`() {
        assertFalse(jumpsToBottom(1))
        assertFalse(jumpsToBottom(100))
        assertTrue(jumpsToBottom(101))
    }
}
