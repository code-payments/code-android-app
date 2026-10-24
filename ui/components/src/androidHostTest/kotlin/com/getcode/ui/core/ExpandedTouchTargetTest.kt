package com.getcode.ui.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ExpandedTouchTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var taps = 0
    private var clicks = 0

    // A 40dp circle spanning 20..60dp, inside a 70dp parent at the root's corner, grown 15dp on
    // every side to 5..75dp. Compose treats a clickable smaller than 48dp as 48dp for hit testing, so the
    // circle's own click also takes taps up to 4dp outside it (16..64dp).
    private fun show() {
        composeTestRule.setContent {
            Box(Modifier.size(100.dp)) {
                Box(Modifier.padding(end = 30.dp, bottom = 30.dp)) {
                    Box(
                        Modifier
                            .offset(20.dp, 20.dp)
                            .expandedTouchTarget(PaddingValues(15.dp)) { taps++ }
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { clicks++ }
                    )
                }
            }
        }
    }

    private fun tapAt(x: Int, y: Int) {
        composeTestRule.onRoot().performTouchInput { click(Offset(x.dp.toPx(), y.dp.toPx())) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a tap on the control fires its own click only`() {
        show()
        tapAt(40, 40)
        assertEquals(0 to 1, taps to clicks)
    }

    @Test
    fun `a tap just outside the control goes to its own click, once`() {
        show()
        tapAt(18, 18)
        assertEquals(0 to 1, taps to clicks)
    }

    @Test
    fun `a tap in the expansion past the control's own reach fires the expansion`() {
        show()
        tapAt(8, 8)
        assertEquals(1 to 0, taps to clicks)
    }

    @Test
    fun `a tap beside the control fires the expansion`() {
        show()
        tapAt(67, 40)
        assertEquals(1 to 0, taps to clicks)
    }

    @Test
    fun `the expansion reaches past the parent's bounds`() {
        show()
        tapAt(73, 40)
        assertEquals(1 to 0, taps to clicks)
    }

    @Test
    fun `a tap past the expansion does nothing`() {
        show()
        tapAt(78, 40)
        assertEquals(0 to 0, taps to clicks)
    }

    /** The outer layout has to hand the parent the control's own size, or siblings would move. */
    @Test
    fun `the expansion leaves the control's layout alone`() {
        var size = IntSize.Zero
        composeTestRule.setContent {
            Box(
                Modifier
                    .onSizeChanged { size = it }
                    .expandedTouchTarget(PaddingValues(15.dp)) { }
                    .size(40.dp)
            )
        }
        composeTestRule.waitForIdle()
        with(composeTestRule.density) { assertEquals(IntSize(40.dp.roundToPx(), 40.dp.roundToPx()), size) }
    }
}
