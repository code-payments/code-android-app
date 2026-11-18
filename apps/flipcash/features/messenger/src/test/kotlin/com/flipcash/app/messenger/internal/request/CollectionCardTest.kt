package com.flipcash.app.messenger.internal.request

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PayerSummary
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.shared.chat.ui.request.PaymentRequestCardContent
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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

    private fun show(request: PaymentRequest) {
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(LocalExchange provides ExchangeStub()) {
                    // A collection's card state carries an empty title (requestCardState); null means loading.
                    PaymentRequestCardContent(request = request, title = "", now = t0)
                }
            }
        }
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
}
