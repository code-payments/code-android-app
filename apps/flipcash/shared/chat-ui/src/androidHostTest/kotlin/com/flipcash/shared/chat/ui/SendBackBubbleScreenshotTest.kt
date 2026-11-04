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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
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
 * Sent Back footer's divider, and writes every frame to `build/screenshots/send_back_bubbles.png`.
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

    private fun show(items: List<Pair<String, ChatListItem.ContentBubble>>) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { (name, item) ->
                        Text(text = name, style = CodeTheme.typography.caption, color = CodeTheme.colors.textSecondary)
                        ContentBubble(item = item, position = BubblePosition.Solo, interactive = false)
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

    @Test
    fun `the return's receiver reads to You`() {
        show(listOf(frames.last()))
        composeRule.onNodeWithText("to You", useUnmergedTree = true).assertExists()
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
