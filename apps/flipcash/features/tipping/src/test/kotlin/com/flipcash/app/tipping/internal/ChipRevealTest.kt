package com.flipcash.app.tipping.internal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives [ChipRevealConnection] on a list laid out like the Chats tab: the chip row as item 0, an
 * empty slot as item 1 (where the Archived row goes), rows after, and the list padded by a bar.
 * Positions are read as [pastPark]: 0 is parked, minus the chip height is fully revealed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ChipRevealTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState
    private var revealed by mutableStateOf(false)
    private var firstFrame: Float? = null

    // xhdpi: 2px per dp.
    private val chipPx = 100f

    private fun setContent() {
        composeRule.setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
            val fling = ScrollableDefaults.flingBehavior()
            val connection = remember(listState, fling) {
                ChipRevealConnection(
                    listState = listState,
                    flingBehavior = fling,
                    isShown = { revealed },
                    canReveal = { true },
                    onRevealed = { revealed = true },
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("list").chipReveal(connection),
                state = listState,
                flingBehavior = fling,
                contentPadding = PaddingValues(top = 56.dp),
            ) {
                item(key = "chips") {
                    Box(Modifier.fillMaxWidth().height(50.dp))
                    if (firstFrame == null) firstFrame = listState.chipVisibleFraction()
                }
                item(key = "archived") {}
                items((0 until 60).toList()) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        composeRule.waitForIdle()
    }

    /** A drag of [dy] px that comes to rest before lifting, so it releases with no velocity. */
    private fun TouchInjectionScope.slowDrag(dy: Float) {
        down(Offset(centerX, 400f))
        repeat(20) { moveBy(Offset(0f, dy / 20), delayMillis = 16) }
        repeat(10) { moveBy(Offset.Zero, delayMillis = 16) }
        up()
    }

    private val pastPark get() = listState.pastPark()

    @Test
    fun `starts parked with the chips hidden from the first frame`() {
        setContent()
        assertEquals(0f, firstFrame!!, 0f)
        assertEquals(0f, pastPark, 0.5f)
        assertEquals(chipPx, listState.chipHeight(), 0.5f)
        // The empty Archived slot is the anchor, not the first row, even at zero height.
        assertEquals(1, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun `a pull past half the chips settles revealed`() {
        setContent()
        // Touch slop eats the start of the drag, so pull well past half.
        composeRule.onNodeWithTag("list").performTouchInput { slowDrag(chipPx * 0.75f + 16f) }
        composeRule.waitForIdle()
        assertEquals(-chipPx, pastPark, 0.5f)
        assertTrue(revealed)
    }

    @Test
    fun `a short pull settles back to parked`() {
        setContent()
        composeRule.onNodeWithTag("list").performTouchInput { slowDrag(chipPx * 0.3f + 16f) }
        composeRule.waitForIdle()
        assertEquals(0f, pastPark, 0.5f)
        assertFalse(revealed)
    }

    @Test
    fun `a fling toward the top from mid-list stops parked`() {
        setContent()
        // Far enough down that the release comes well before the top: the fling has to stop.
        runBlocking { listState.scrollToItem(30) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("list").performTouchInput {
            swipeDown(startY = 200f, endY = 1_200f, durationMillis = 80)
        }
        var frames = 0
        while (pastPark > 0.5f && frames++ < 300) composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0f, pastPark, 0.5f)
        // The fling ends where it stops, rather than running on with every delta held back: a
        // fling still running would swallow the next tap on a row.
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        assertFalse("fling still running at park", listState.isScrollInProgress)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0f, pastPark, 0.5f)
        assertFalse(revealed)
    }

    @Test
    fun `a drag from mid-list past the top stops parked while the finger is down`() {
        setContent()
        runBlocking { listState.scrollToItem(3) }
        composeRule.waitForIdle()
        val list = composeRule.onNodeWithTag("list")
        list.performTouchInput {
            down(Offset(centerX, 300f))
            repeat(20) { moveBy(Offset(0f, 50f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        assertEquals(0f, pastPark, 0.5f)
        list.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0f, pastPark, 0.5f)
        assertFalse(revealed)
    }

    @Test
    fun `a pull from the top follows the finger`() {
        setContent()
        val list = composeRule.onNodeWithTag("list")
        list.performTouchInput {
            down(Offset(centerX, 400f))
            // Past touch slop, then 40px more.
            repeat(4) { moveBy(Offset(0f, 20f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        val partway = pastPark
        list.performTouchInput { moveBy(Offset(0f, 30f), delayMillis = 16) }
        composeRule.waitForIdle()
        assertTrue("chips partly on: $partway", partway < 0f && partway > -chipPx)
        assertEquals(partway - 30f, pastPark, 0.5f)
        list.performTouchInput { up() }
    }

    @Test
    fun `once revealed the list scrolls freely to the chips`() {
        setContent()
        revealed = true
        runBlocking { listState.scrollToItem(8) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("list").performTouchInput {
            swipeDown(startY = 200f, endY = 1_200f, durationMillis = 80)
        }
        composeRule.waitForIdle()
        assertEquals(-chipPx, pastPark, 0.5f)
    }
}
