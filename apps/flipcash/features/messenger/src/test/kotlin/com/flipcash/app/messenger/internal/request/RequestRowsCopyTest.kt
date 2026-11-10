package com.flipcash.app.messenger.internal.request

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.messenger.internal.screens.components.RequestNoticeRow
import com.flipcash.app.messenger.internal.screens.components.SharePaymentRow
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.ui.request.LocalPaymentRequestCards
import com.flipcash.shared.chat.ui.request.PaymentRequestCards
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.DesignSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What the share payment, marked-paid and closed rows read for each viewer, and what a tap does
 * (spec cases 63, 68 and 73, and the copy of "Marked paid and closed rows").
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RequestRowsCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val chat = ChatId(ByteArray(32) { 3 })
    private val alex: ID = List(16) { 1 }
    private val jordan: ID = List(16) { 2 }
    private val sam: ID = List(16) { 3 }
    private val requestId: ID = List(16) { 9 }
    private val t0 = Instant.fromEpochSeconds(1_000)
    private val forty = Fiat(40.0, CurrencyCode.USD)
    private val ten = Fiat(10.0, CurrencyCode.USD)

    private val request = PaymentRequest(
        id = requestId,
        creator = alex,
        createdAt = t0,
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chat,
            split = Split(
                text = PaymentRequestText.Plain("Tahoe cabin", ""),
                currency = CurrencyCode.USD,
                endsAt = t0 + 240.hours,
                shares = listOf(
                    Share(alex, forty, PaidShare(t0, PaidShare.How.Creator)),
                    Share(jordan, forty, PaidShare(t0 + 1.hours, PaidShare.How.Payment)),
                    Share(sam, forty, null),
                ),
            ),
        ),
    )

    private var opened: ID? = null

    private fun payment(
        id: Long = 1,
        fromSelf: Boolean = false,
        toViewer: Boolean = false,
        payerName: String = "Jordan",
        amount: Fiat = forty,
    ) = ChatListItem.SharePayment(
        messageId = id,
        contentIndex = 0,
        timestamp = t0 + 1.hours,
        isFromSelf = fromSelf,
        requestId = requestId,
        payerId = jordan,
        payerName = payerName,
        receiverId = alex,
        receiverName = "Alex",
        receiverIsViewer = toViewer,
        amount = amount,
    )

    private fun show(
        held: Boolean = true,
        missing: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val cards = PaymentRequestCards(
            chatId = chat,
            viewerId = sam,
            requests = if (held) mapOf(requestId to request) else emptyMap(),
            missing = if (missing) setOf(requestId) else emptySet(),
            now = t0 + 2.hours,
            onNeeded = {},
            onOpen = { opened = it },
        )
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalExchange provides ExchangeStub(),
                    LocalPaymentRequestCards provides cards,
                    content = content,
                )
            }
        }
    }

    // Case 63.
    @Test
    fun `the debtor reads you paid`() {
        show { SharePaymentRow(listOf(payment(fromSelf = true))) }
        composeTestRule.onNodeWithText("You paid \$40 · Tahoe cabin").assertExists()
    }

    @Test
    fun `the creator reads paid you`() {
        show { SharePaymentRow(listOf(payment(toViewer = true))) }
        composeTestRule.onNodeWithText("Jordan paid you \$40 · Tahoe cabin").assertExists()
    }

    @Test
    fun `another member reads both names`() {
        show { SharePaymentRow(listOf(payment())) }
        composeTestRule.onNodeWithText("Jordan paid Alex \$40 · Tahoe cabin").assertExists()
    }

    @Test
    fun `a tap on the row opens the request`() {
        show { SharePaymentRow(listOf(payment())) }
        composeTestRule.onNodeWithText("Jordan paid Alex \$40 · Tahoe cabin").assertHasClickAction().performClick()
        assertEquals(requestId, opened)
    }

    @Test
    fun `a run reads its payer count and the sum`() {
        show { SharePaymentRow(List(3) { payment(id = 3L - it, amount = ten) }) }
        composeTestRule.onNodeWithText("3 people paid \$30 · Tahoe cabin").assertExists()
    }

    @Test
    fun `a run paid to the viewer says so`() {
        show { SharePaymentRow(List(3) { payment(id = 3L - it, toViewer = true, amount = ten) }) }
        composeTestRule.onNodeWithText("3 people paid you \$30 · Tahoe cabin").assertExists()
    }

    // Case 68.
    @Test
    fun `a run of seven with the viewer among the payers`() {
        show { SharePaymentRow(List(7) { payment(id = 7L - it, fromSelf = it == 4, amount = ten) }) }
        composeTestRule.onNodeWithText("You and 6 others paid \$70 · Tahoe cabin").assertExists()
    }

    // Case 73.
    @Test
    fun `a share payment whose request is not found is the caption alone and not tappable`() {
        show(held = false, missing = true) { SharePaymentRow(listOf(payment())) }
        composeTestRule.onNodeWithText("Jordan paid Alex \$40").assertExists().assertHasNoClickAction()
    }

    private fun notice(kind: ChatListItem.RequestNotice.Kind, creatorName: String?) = ChatListItem.RequestNotice(
        key = "k",
        requestId = requestId,
        timestamp = t0 + 1.hours,
        kind = kind,
        creatorName = creatorName,
        title = "Tahoe cabin",
    )

    private fun marked(vararg people: Pair<String, Boolean>) = ChatListItem.RequestNotice.Kind.MarkedPaid(
        marked = people.mapIndexed { i, (name, isViewer) ->
            ChatListItem.RequestNotice.Person(List(16) { (20 + i).toByte() }, name, isViewer)
        },
        progress = 0.5f,
    )

    @Test
    fun `a marked-paid row names the creator and the member`() {
        show { RequestNoticeRow(notice(marked("Sam" to false), creatorName = "Alex")) }
        composeTestRule.onNodeWithText("Alex marked Sam as paid · Tahoe cabin").assertExists()
    }

    @Test
    fun `the creator reads you marked`() {
        show { RequestNoticeRow(notice(marked("Sam" to false), creatorName = null)) }
        composeTestRule.onNodeWithText("You marked Sam as paid · Tahoe cabin").assertExists()
    }

    @Test
    fun `the marked member reads marked you`() {
        show { RequestNoticeRow(notice(marked("Sam" to true), creatorName = "Alex")) }
        composeTestRule.onNodeWithText("Alex marked you as paid · Tahoe cabin").assertExists()
    }

    // Case 80's copy.
    @Test
    fun `a run of marks reads its count`() {
        show { RequestNoticeRow(notice(marked("Sam" to false, "Jordan" to false, "Riley" to false), "Alex")) }
        composeTestRule.onNodeWithText("Alex marked 3 people as paid · Tahoe cabin").assertExists()
    }

    @Test
    fun `a closed row names who closed it and opens the request`() {
        show { RequestNoticeRow(notice(ChatListItem.RequestNotice.Kind.Closed, creatorName = "Alex")) }
        composeTestRule.onNodeWithText("Alex closed Tahoe cabin").assertHasClickAction().performClick()
        assertEquals(requestId, opened)
    }

    @Test
    fun `the creator reads you closed`() {
        show { RequestNoticeRow(notice(ChatListItem.RequestNotice.Kind.Closed, creatorName = null)) }
        composeTestRule.onNodeWithText("You closed Tahoe cabin").assertExists()
    }
}
