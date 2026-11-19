package com.flipcash.app.messenger.internal.request

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.test.assertEquals
import kotlin.time.Instant

/** What a collection's chat card reads: what each member pays, what came in, and "Closed" once closed. */
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

    // A collection's card state carries an empty title (requestCardState); null means loading.
    private fun show(request: PaymentRequest, title: String = "") {
        composeTestRule.setContent {
            DesignSystem {
                titleStyle = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.textMain)
                progressStyle = CodeTheme.typography.textSmall.copy(color = CodeTheme.colors.textMain.copy(alpha = 0.75f))
                CompositionLocalProvider(LocalExchange provides ExchangeStub()) {
                    PaymentRequestCardContent(request = request, title = title, now = t0)
                }
            }
        }
    }

    private fun styleOf(text: String): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style
    }

    private fun assertDrawnIn(expected: TextStyle, text: String) {
        val actual = styleOf(text)
        assertEquals(expected.fontSize, actual.fontSize, "font size of \"$text\"")
        assertEquals(expected.fontWeight, actual.fontWeight, "font weight of \"$text\"")
        assertEquals(expected.color, actual.color, "colour of \"$text\"")
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
        assertDrawnIn(progressStyle, "\$10 each")
    }

    @Test
    fun `a split keeps its title bold and its progress quiet`() {
        show(split(), title = "Dinner")
        assertDrawnIn(titleStyle, "Dinner")
        assertDrawnIn(progressStyle, "\$0 paid, \$0 requested")
    }
}
