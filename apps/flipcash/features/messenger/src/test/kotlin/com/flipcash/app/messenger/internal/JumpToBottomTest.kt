package com.flipcash.app.messenger.internal

import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.jumpToBottomBadgeInset
import com.flipcash.app.messenger.internal.screens.components.jumpToBottomBottomOffset
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

    @Test
    fun `button's bottom edge is twice the composer gap above the dollar circle`() {
        val barHeight = 80.dp
        val barPad = 12.dp
        val gap = 8.dp
        val offset = jumpToBottomBottomOffset(barHeight, barPad, gap)
        // The button's bottom edge rises (offset - barHeight) above the bar's top; the circle's top is
        // barPad below it. The gap between the two is the sum.
        assertEquals(gap * 2, (offset - barHeight) + barPad)
    }

    @Test
    fun `badge centre sits on the circle's edge at 45 degrees`() {
        // 48/2 * (1 - 1/sqrt(2)) = 7.03dp in from the bounding box's corner on both axes.
        assertEquals(7.03f, jumpToBottomBadgeInset(48.dp).value, 0.01f)
    }
}
