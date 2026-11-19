package com.flipcash.app.messenger.internal.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PayerSummary
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.ui.request.PaymentRequestCardContent
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a collection's chat card reads: what each member pays, what came in, and "Closed" once
 * closed. It stacks those lines with no glyph (frame 1.13) and dims to half once ended, as iOS
 * does; a split keeps its glyph and is never dimmed. The placeholder is the one that shipped, glyph
 * included, since a loading card can't tell a split from a collection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class CollectionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val t0 = Instant.fromEpochSeconds(1_000)
    private val creator: ID = List(16) { 1 }

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD)

    private fun collection(status: PaymentRequestStatus = PaymentRequestStatus.Open, paid: Fiat = usd(0.0)) = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = t0,
        status = status,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = ChatId(ByteArray(32) { 3 }),
            collection = PaymentCollection(
                text = PaymentRequestText.Plain("", ""),
                currency = CurrencyCode.USD,
                endsAt = null,
                amount = CollectionAmount.Fixed(usd(10.0)),
            ),
        ),
        payerSummary = PayerSummary(payerCount = 0, paidCount = 0, amountDueQuarks = 0, amountPaidQuarks = paid.quarks),
    )

    private fun split() = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = t0,
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(ByteArray(32) { 3 }),
            split = Split(PaymentRequestText.Plain("Dinner", ""), CurrencyCode.USD, Instant.fromEpochSeconds(100_000), emptyList()),
        ),
    )

    private lateinit var titleStyle: TextStyle
    private lateinit var progressStyle: TextStyle
    private lateinit var quietStyle: TextStyle
    private lateinit var captionStyle: TextStyle

    // A collection's card state carries an empty title (requestCardState); null means loading.
    private fun show(request: PaymentRequest?, title: String? = "") = showAll(request to title)

    // Each card on black, so a card's brightness reads straight off its pixels.
    private fun showAll(vararg cards: Pair<PaymentRequest?, String?>) {
        composeTestRule.setContent {
            DesignSystem {
                titleStyle = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.textMain)
                progressStyle = CodeTheme.typography.textSmall.copy(color = CodeTheme.colors.textMain.copy(alpha = 0.75f))
                quietStyle = CodeTheme.typography.textSmall.copy(color = CodeTheme.colors.textSecondary)
                captionStyle = CodeTheme.typography.caption.copy(color = CodeTheme.colors.textSecondary)
                CompositionLocalProvider(LocalExchange provides ExchangeStub()) {
                    Column(Modifier.background(Color.Black)) {
                        cards.forEach { (request, title) ->
                            PaymentRequestCardContent(request = request, title = title, now = t0)
                        }
                    }
                }
            }
        }
    }

    /** The brightest red value [text]'s pixels reach, 0 to 255. */
    private fun brightestIn(text: String): Int {
        val pixels = composeTestRule.onNodeWithText(text, useUnmergedTree = true).captureToImage().toPixelMap()
        var brightest = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                brightest = maxOf(brightest, (pixels[x, y].red * 255).roundToInt())
            }
        }
        return brightest
    }

    private fun styleOf(node: SemanticsNodeInteraction): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style
    }

    private fun assertDrawnIn(expected: TextStyle, text: String) =
        assertDrawnIn(expected, composeTestRule.onNodeWithText(text, useUnmergedTree = true), "\"$text\"")

    private fun assertDrawnIn(expected: TextStyle, node: SemanticsNodeInteraction, name: String) {
        val actual = styleOf(node)
        assertEquals(expected.fontSize, actual.fontSize, "font size of $name")
        assertEquals(expected.fontWeight, actual.fontWeight, "font weight of $name")
        assertEquals(expected.color, actual.color, "colour of $name")
    }

    @Test
    fun `an open collection reads what each member pays and what came in, and is not closed`() {
        show(collection(paid = usd(20.0)))
        composeTestRule.onNodeWithText("\$10 each").assertExists()
        composeTestRule.onNodeWithText("\$20 paid").assertExists()
        composeTestRule.onNodeWithText("Closed").assertDoesNotExist()
    }

    @Test
    fun `a closed collection reads Closed under its total`() {
        show(collection(status = PaymentRequestStatus.Closed))
        composeTestRule.onNodeWithText("\$10 each").assertExists()
        composeTestRule.onNodeWithText("\$0 paid").assertExists()
        composeTestRule.onNodeWithText("Closed").assertExists()
    }

    @Test
    fun `a collection leads with what came in, and its amount each is the quiet line`() {
        show(collection(paid = usd(20.0)))
        assertDrawnIn(titleStyle, "\$20 paid")
        assertDrawnIn(quietStyle, "\$10 each")
    }

    @Test
    fun `a collection draws no glyph`() {
        show(collection(paid = usd(20.0)))
        composeTestRule.onNodeWithText("\$", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a collection stacks Request, its amount each, then what came in, down one edge`() {
        show(collection(paid = usd(20.0)))
        val lines = listOf("Request", "\$10 each", "\$20 paid").map {
            composeTestRule.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
        }
        lines.zipWithNext().forEach { (above, below) ->
            assertTrue(above.bottom <= below.top, "expected $above above $below")
        }
        assertEquals(1, lines.map { it.left }.distinct().size, "left edges of ${lines.map { it.left }}")
    }

    @Test
    fun `a split keeps its title bold and its progress quiet`() {
        show(split(), title = "Dinner")
        assertDrawnIn(titleStyle, "Dinner")
        assertDrawnIn(progressStyle, "\$0 paid, \$0 requested")
    }

    @Test
    fun `a split keeps its glyph`() {
        show(split(), title = "Dinner")
        composeTestRule.onNodeWithText("\$", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a collection sets its label and status line in the small text style, as iOS does`() {
        show(collection(status = PaymentRequestStatus.Closed))
        assertDrawnIn(quietStyle, "Request")
        assertDrawnIn(quietStyle, "Closed")
    }

    @Test
    fun `a split keeps its caption label`() {
        show(split(), title = "Dinner")
        assertDrawnIn(captionStyle, "Request")
    }

    @Test
    fun `the placeholder is the one that shipped, glyph and all`() {
        show(request = null, title = null)
        composeTestRule.onNodeWithText("\$", useUnmergedTree = true).assertExists()
        assertDrawnIn(captionStyle, "Request")
        // The blank title, progress and status lines keep the styles, and so the heights, they
        // shipped with.
        val blanks = composeTestRule.onAllNodesWithText("", useUnmergedTree = true)
        blanks.assertCountEquals(3)
        listOf(titleStyle, progressStyle, captionStyle).forEachIndexed { line, style ->
            assertDrawnIn(style, blanks[line], "blank line $line")
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an ended collection dims to half`() {
        showAll(
            collection(paid = usd(20.0)) to "",
            collection(status = PaymentRequestStatus.Closed, paid = usd(30.0)) to "",
        )
        val open = brightestIn("\$20 paid")
        val ended = brightestIn("\$30 paid")
        val ratio = ended.toFloat() / open
        assertTrue(ratio in 0.4f..0.6f, "ended $ended against open $open")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an ended split is not dimmed`() {
        showAll(
            split() to "Dinner",
            split().copy(status = PaymentRequestStatus.Closed) to "Lunch",
        )
        val open = brightestIn("Dinner")
        val ended = brightestIn("Lunch")
        assertTrue(ended >= open * 0.95f, "ended $ended against open $open")
    }
}
