package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SendBackRow
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * The Send Back frames of a DM's opening payment and its return (`docs/specs/send-back.md` in the
 * orchestrator): the offered payment with each row, and the return as each side reads it. Checks the
 * Sent Back footer's divider and spacing, and writes every frame to `build/screenshots/send_back_bubbles.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h2000dp-xhdpi")
class SendBackBubbleScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun cash(paymentReturn: MessageContent.Cash.PaymentReturn) = MessageContent.Cash(
        intentId = listOf(0),
        amount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD),
        mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa"),
        tokenName = "Dollars",
        paymentReturn = paymentReturn,
    )

    private val offered = cash(MessageContent.Cash.PaymentReturn(offer = MessageContent.Cash.PaymentReturn.Offer))
    private val returned = cash(
        MessageContent.Cash.PaymentReturn(returnOf = MessageContent.Cash.PaymentReturn.ReturnOf(1)),
    )
    private val dm = CashBubblePeople.Direct(other = "Alex Morgan")

    private fun bubble(content: MessageContent.Cash, isFromSelf: Boolean, sendBack: SendBackRow? = null) =
        ChatListItem.ContentBubble(
            messageId = 42,
            contentIndex = 0,
            content = content,
            isFromSelf = isFromSelf,
            timestamp = Instant.fromEpochSeconds(1_000),
            cashPeople = dm,
            sendBack = sendBack,
        )

    private val frames = listOf(
        "S1 Available, receiver" to bubble(offered, isFromSelf = false, sendBack = SendBackRow.Available),
        "S5 Sent Back, sender" to bubble(offered, isFromSelf = true, sendBack = SendBackRow.SentBack),
        "S6 Sent Back, receiver" to bubble(offered, isFromSelf = false, sendBack = SendBackRow.SentBack),
        "8 Return, its sender" to bubble(returned, isFromSelf = true),
        "9 Return, its receiver" to bubble(returned, isFromSelf = false),
    )

    private var footerHeight = Dp.Unspecified
    private var rowHeight = Dp.Unspecified
    private var hairline = Dp.Unspecified

    private fun show(items: List<Pair<String, ChatListItem.ContentBubble>>) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                footerHeight = CodeTheme.dimens.grid.x9
                rowHeight = CodeTheme.dimens.grid.x11
                hairline = CodeTheme.dimens.border
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { (name, item) ->
                        Text(text = name, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
                        Box(modifier = Modifier.testTag(name)) {
                            ContentBubble(item = item, position = BubblePosition.Solo, interactive = false)
                        }
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `only a Sent Back footer draws the divider`() {
        show(frames)
        // S5 and S6; not the Send Back row and not either return bubble.
        composeRule.onAllNodesWithTag(SENT_BACK_DIVIDER_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    private fun height(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        .getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun assertDp(expected: Dp, actual: Dp, what: String) =
        assertEquals(what, expected.value, actual.value, 0.5f)

    @Test
    fun `the last line has the same gap above the Sent Back divider as above the Send Back row`() {
        show(frames.filter { it.first.startsWith("S1") || it.first.startsWith("S6") })
        val lines = composeRule.onAllNodesWithText("from Alex Morgan", useUnmergedTree = true)
        val aboveRow = composeRule.onNodeWithTag(SEND_BACK_ROW_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top - lines[0].getUnclippedBoundsInRoot().bottom
        val aboveDivider = composeRule.onNodeWithTag(SENT_BACK_DIVIDER_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top - lines[1].getUnclippedBoundsInRoot().bottom
        assertDp(aboveRow, aboveDivider, "gap above the divider")
    }

    @Test
    fun `the Sent Back footer sits below the bottom padding`() {
        val bare = "offered, no row" to bubble(offered, isFromSelf = false)
        show(listOf(bare) + frames.filter { it.first.startsWith("S1") || it.first.startsWith("S6") })
        assertDp(footerHeight, height(SENT_BACK_FOOTER_TAG), "footer band")
        // The bare bubble already ends in its bottom padding; the footer adds its hairline and band.
        assertDp(height(bare.first) + hairline + footerHeight, height("S6 Sent Back, receiver"), "S6 over bare")
        // Same body as S1, with the band in place of the white row.
        assertDp(
            height("S1 Available, receiver") - rowHeight + hairline + footerHeight,
            height("S6 Sent Back, receiver"),
            "S6 against S1",
        )
    }

    @Test
    fun `the return's receiver reads to You`() {
        show(listOf(frames.last()))
        composeRule.onNodeWithText("to You", useUnmergedTree = true).assertExists()
    }

    private fun width(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        .getUnclippedBoundsInRoot().let { it.right - it.left }

    @Test
    fun `the Send Back row and the Sent Back footer never change the bubble's width`() {
        val bare = "offered, no row" to bubble(offered, isFromSelf = false)
        show(listOf(bare) + frames.filter { it.first.startsWith("S1") || it.first.startsWith("S6") })
        assertDp(width(bare.first), width("S1 Available, receiver"), "S1 width")
        assertDp(width(bare.first), width("S6 Sent Back, receiver"), "S6 width")
    }

    /**
     * A bare offered bubble tagged `bare` beside one tagged `live` whose Send Back state the test
     * changes after it has drawn, the way the transcript gets it when the return state loads late.
     */
    private fun showLive(initial: SendBackRow?): MutableState<SendBackRow?> {
        val state = mutableStateOf(initial)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                footerHeight = CodeTheme.dimens.grid.x9
                rowHeight = CodeTheme.dimens.grid.x11
                hairline = CodeTheme.dimens.border
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.testTag("bare")) {
                        ContentBubble(item = bubble(offered, isFromSelf = false), position = BubblePosition.Solo, interactive = false)
                    }
                    Box(modifier = Modifier.testTag("live")) {
                        ContentBubble(
                            item = bubble(offered, isFromSelf = false, sendBack = state.value),
                            position = BubblePosition.Solo,
                            interactive = false,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        return state
    }

    private fun assertGrowsIn(arriving: SendBackRow, added: () -> Dp) {
        val state = showLive(initial = null)
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val bare = height("bare")
        assertDp(bare, height("live"), "before it arrives")

        composeRule.runOnUiThread { state.value = arriving }
        // Applies the write so the next frames recompose with it; the clock does not move.
        composeRule.waitForIdle()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        val mid = height("live")
        assertTrue("mid-animation height $mid should be between $bare and ${bare + added()}", mid.value > bare.value + 0.5f && mid.value < (bare + added()).value - 0.5f)
        assertDp(width("bare"), width("live"), "width while it grows")

        composeRule.mainClock.advanceTimeBy(3_000)
        assertDp(bare + added(), height("live"), "settled")
        assertDp(width("bare"), width("live"), "width once settled")
    }

    @Test
    fun `a Sent Back footer that arrives late grows in`() = assertGrowsIn(SendBackRow.SentBack) { hairline + footerHeight }

    @Test
    fun `a Send Back row that arrives late grows in`() = assertGrowsIn(SendBackRow.Available) { rowHeight }

    @Test
    fun `a footer known when the bubble first draws is there at full height`() {
        showLive(initial = SendBackRow.SentBack)
        assertDp(height("bare") + hairline + footerHeight, height("live"), "first frame")
    }

    @Test
    fun rendersEveryFrame() {
        show(frames)
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, "send_back_bubbles.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
