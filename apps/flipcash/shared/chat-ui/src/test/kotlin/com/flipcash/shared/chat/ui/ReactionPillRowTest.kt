package com.flipcash.shared.chat.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.reactions.ReactionPill
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReactionPillRow]: the pill row a bubble draws its reaction summary in. Behavior, not pixels —
 * callback wiring, the previewer's read-only contract (decision 2, #17), and the "N more" collapse
 * an narrow bubble forces (the layout math itself is [ReactionPillRowLayoutTest]'s job).
 */
@RunWith(RobolectricTestRunner::class)
class ReactionPillRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pill(
        emoji: String,
        count: Long = 1,
        selfReacted: Boolean = false,
        pending: Boolean = false,
    ) = ReactionPill(emoji = emoji, count = count, selfReacted = selfReacted, pending = pending)

    private fun setRow(
        pills: List<ReactionPill>,
        canReact: Boolean = true,
        width: androidx.compose.ui.unit.Dp = 400.dp,
        onToggle: (String) -> Unit = {},
        onPillLongClick: () -> Unit = {},
        onOpenPicker: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                Box(Modifier.width(width)) {
                    ReactionPillRow(
                        pills = pills,
                        canReact = canReact,
                        onToggle = onToggle,
                        onPillLongClick = onPillLongClick,
                        onOpenPicker = onOpenPicker,
                    )
                }
            }
        }
    }

    @Test
    fun `a message with no reactions draws no row and no plus, matching iOS`() {
        setRow(pills = emptyList(), canReact = true)

        composeTestRule.onNodeWithTag("reaction_pill_plus").assertDoesNotExist()
    }

    @Test
    fun `a removed pill stays on screen while it animates out, then goes`() {
        var pills by mutableStateOf(listOf(pill("😀"), pill("🎉")))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPillRowClock provides { composeTestRule.mainClock.currentTime }) {
                DesignSystem {
                    Box(Modifier.width(400.dp)) {
                        ReactionPillRow(pills = pills, canReact = true, onToggle = {}, onPillLongClick = {}, onOpenPicker = {})
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("reaction_pill_🎉").assertIsDisplayed()

        pills = listOf(pill("😀"))
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("reaction_pill_🎉").assertExists()

        // It leaves on the next beat, a second after the row appeared, then fades.
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithTag("reaction_pill_🎉").assertExists()

        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithTag("reaction_pill_🎉").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reaction_pill_😀").assertIsDisplayed()
    }

    @Test
    fun `losing the last pill collapses the row to no height`() {
        var pills by mutableStateOf(listOf(pill("😀")))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPillRowClock provides { composeTestRule.mainClock.currentTime }) {
                DesignSystem {
                    Box(Modifier.width(400.dp).testTag("container")) {
                        ReactionPillRow(pills = pills, canReact = true, onToggle = {}, onPillLongClick = {}, onOpenPicker = {})
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("container").assertHeightIsEqualTo(32.dp)

        pills = emptyList()
        // The emptied pill leaves on the beat.
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("container").assertHeightIsEqualTo(0.dp)
        composeTestRule.onNodeWithTag("reaction_pill_😀").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reaction_pill_plus").assertDoesNotExist()
    }

    @Test
    fun `plus is a 28dp circle`() {
        setRow(pills = listOf(pill("😀")))

        composeTestRule.onNodeWithTag("reaction_pill_plus")
            .assertWidthIsEqualTo(28.dp)
            .assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun `pills and the N more pill are 28dp tall, level with plus`() {
        val pills = listOf(
            pill("😀"), pill("😁"), pill("😂"), pill("😃"),
            pill("😄"), pill("😅"), pill("😆"), pill("😇"),
        )
        setRow(pills = pills, width = 90.dp)

        composeTestRule.onNodeWithTag("reaction_pill_😀").assertHeightIsEqualTo(28.dp)
        composeTestRule.onNodeWithTag("reaction_pill_more").assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun `tapping a pill toggles that emoji`() {
        val toggled = mutableListOf<String>()
        setRow(pills = listOf(pill("😀"), pill("😂")), onToggle = { toggled += it })

        composeTestRule.onNodeWithTag("reaction_pill_😂").performClick()

        composeTestRule.runOnIdle { assertEquals(listOf("😂"), toggled) }
    }

    @Test
    fun `long-pressing a pill only opens reactors, and does not reach a parent long-press`() {
        val toggled = mutableListOf<String>()
        var reactorsOpened = 0
        var parentLongPresses = 0

        composeTestRule.setContent {
            DesignSystem {
                Box(
                    Modifier
                        .width(400.dp)
                        .combinedClickable(onClick = {}, onLongClick = { parentLongPresses++ }),
                ) {
                    ReactionPillRow(
                        pills = listOf(pill("😀"), pill("😂")),
                        canReact = true,
                        onToggle = { toggled += it },
                        onPillLongClick = { reactorsOpened++ },
                        onOpenPicker = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("reaction_pill_😂").performTouchInput { longClick() }

        composeTestRule.runOnIdle {
            assertEquals(1, reactorsOpened)
            assertEquals(emptyList<String>(), toggled)
            assertEquals(0, parentLongPresses)
        }
    }

    @Test
    fun `plus opens the picker`() {
        var opened = 0
        setRow(pills = listOf(pill("😀")), onOpenPicker = { opened++ })

        composeTestRule.onNodeWithTag("reaction_pill_plus").performClick()

        composeTestRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun `a previewer sees pills and can open reactors, but has no plus and taps do nothing`() {
        val toggled = mutableListOf<String>()
        var reactorsOpened = 0
        setRow(
            pills = listOf(pill("😀")),
            canReact = false,
            onToggle = { toggled += it },
            onPillLongClick = { reactorsOpened++ },
        )

        composeTestRule.onNodeWithTag("reaction_pill_plus").assertDoesNotExist()

        composeTestRule.onNodeWithTag("reaction_pill_😀").performClick()
        composeTestRule.onNodeWithTag("reaction_pill_😀").performTouchInput { longClick() }

        composeTestRule.runOnIdle {
            assertTrue(toggled.isEmpty())
            assertEquals(1, reactorsOpened)
        }
    }

    @Test
    fun `a selected pill is exposed as selected in semantics`() {
        setRow(pills = listOf(pill("😀", selfReacted = true), pill("😂", selfReacted = false)))

        composeTestRule.onNodeWithTag("reaction_pill_😀").assertIsSelected()
        composeTestRule.onNodeWithTag("reaction_pill_😂").assertIsNotSelected()
    }

    @Test
    fun `wrapping at a narrow width collapses into an N more pill before plus, which expands on tap`() {
        val pills = listOf(
            pill("😀"), pill("😁"), pill("😂"), pill("😃"),
            pill("😄"), pill("😅"), pill("😆"), pill("😇"),
        )
        setRow(pills = pills, width = 90.dp)

        // Narrow enough that not everything fits on two lines: a collapse pill stands in for the
        // pills pushed off, placed ahead of "+". The row's SubcomposeLayout still subcomposes every
        // pill to measure it, so a collapsed one stays in the semantics tree unplaced — displayed,
        // not existence, is the check that actually distinguishes "shown" from "collapsed away".
        composeTestRule.onNodeWithTag("reaction_pill_more").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reaction_pill_😇").assertIsNotDisplayed()

        composeTestRule.onNodeWithTag("reaction_pill_more").performClick()

        composeTestRule.runOnIdle {
            composeTestRule.onNodeWithTag("reaction_pill_more").assertDoesNotExist()
            composeTestRule.onNodeWithTag("reaction_pill_😇").assertIsDisplayed()
        }
    }

    /** A row on the compose test clock, so its beats follow `mainClock`. */
    private fun setSettlingRow(pills: () -> List<ReactionPill>, onToggle: (String) -> Unit = {}) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPillRowClock provides { composeTestRule.mainClock.currentTime }) {
                DesignSystem {
                    Box(Modifier.width(400.dp)) {
                        ReactionPillRow(
                            pills = pills(),
                            canReact = true,
                            onToggle = onToggle,
                            onPillLongClick = {},
                            onOpenPicker = {},
                            modifier = Modifier.testTag("row"),
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun widthOf(tag: String) = composeTestRule.onNodeWithTag(tag).getBoundsInRoot().let { it.right - it.left }

    @Test
    fun `a count crossing 99 keeps the pill's width until the beat, then widens on it`() {
        var pills by mutableStateOf(listOf(pill("😀", count = 99)))
        setSettlingRow({ pills })
        val before = widthOf("reaction_pill_😀")

        pills = listOf(pill("😀", count = 100))
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithText("99").assertExists()
        assertEquals(before, widthOf("reaction_pill_😀"))

        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithText("100").assertExists()
        assertTrue(widthOf("reaction_pill_😀") > before, "the pill widens on the beat")
    }

    @Test
    fun `a count that fits its reserved room shows at once without changing the width`() {
        var pills by mutableStateOf(listOf(pill("😀", count = 5)))
        setSettlingRow({ pills })
        val before = widthOf("reaction_pill_😀")

        pills = listOf(pill("😀", count = 42))
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText("42").assertExists()
        assertEquals(before, widthOf("reaction_pill_😀"))
    }

    @Test
    fun `selecting a pill does not change its width`() {
        var pills by mutableStateOf(listOf(pill("😀", count = 3)))
        setSettlingRow({ pills })
        val before = widthOf("reaction_pill_😀")

        pills = listOf(pill("😀", count = 3, selfReacted = true))
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        assertEquals(before, widthOf("reaction_pill_😀"))
    }

    @Test
    fun `positions change on the beat, and a tap right after a swap lands on the pill that was there`() {
        var pills by mutableStateOf(listOf(pill("😂", count = 5), pill("🔥", count = 4)))
        val toggled = mutableListOf<String>()
        setSettlingRow({ pills }, onToggle = { toggled += it })
        val rowLeft = composeTestRule.onNodeWithTag("row").getBoundsInRoot()
        val firstSlot = composeTestRule.onNodeWithTag("reaction_pill_😂").getBoundsInRoot()
        val slotCenter = Offset(
            x = ((firstSlot.left + firstSlot.right) / 2 - rowLeft.left).value,
            y = ((firstSlot.top + firstSlot.bottom) / 2 - rowLeft.top).value,
        )

        pills = listOf(pill("😂", count = 5), pill("🔥", count = 6))
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(500)
        assertTrue(
            composeTestRule.onNodeWithTag("reaction_pill_😂").getBoundsInRoot().left <
                composeTestRule.onNodeWithTag("reaction_pill_🔥").getBoundsInRoot().left,
            "nothing moves before the beat",
        )

        // The beat swaps them; a tap 100 ms later on the first slot still gets 😂.
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onNodeWithTag("row").performTouchInput { click(slotCenter * density) }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf("😂"), toggled)

        // Past the grace, the same spot is 🔥.
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithTag("row").performTouchInput { click(slotCenter * density) }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf("😂", "🔥"), toggled)
    }
}
