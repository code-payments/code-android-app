package com.flipcash.app.messenger.internal.request

import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
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
import kotlin.time.Instant

/**
 * The request stack under the app bar for every viewer: Pay and the owed line only on a share
 * still owed, changing in place when it is paid, and no dock on a drag up (spec rows 24, 25, 27).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RequestDeckStripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val creator: ID = List(16) { 1 }
    private val debtor: ID = List(16) { 2 }
    private val payer: ID = List(16) { 3 }
    private val now = Instant.fromEpochSeconds(500)
    private val twenty = Fiat(20.0, CurrencyCode.USD)
    private val payTwenty = "Pay $20"

    private fun request(id: Byte = 9, title: String = "Dinner", debtorPaid: Boolean = false) = PaymentRequest(
        id = List(16) { id },
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(ByteArray(32) { 3 }),
            split = Split(
                text = PaymentRequestText.Plain(title, ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(500 + 5 * 86_400L),
                shares = listOf(
                    Share(creator, twenty, PaidShare(Instant.fromEpochSeconds(0), PaidShare.How.Creator)),
                    Share(debtor, twenty, if (debtorPaid) PaidShare(Instant.fromEpochSeconds(20), PaidShare.How.Payment) else null),
                    Share(payer, twenty, PaidShare(Instant.fromEpochSeconds(10), PaidShare.How.Payment)),
                ),
            ),
        ),
    )

    private var cycles = 0

    private fun show(deck: () -> List<PaymentRequest>, viewer: ID) {
        composeTestRule.setContent {
            DesignSystem {
                RequestDeckLayout {
                    RequestDeckStrip(
                        deck = deck(),
                        now = now,
                        viewerId = viewer,
                        transition = updateTransition(DeckPlacement.Strip, label = "test"),
                        onExpand = {},
                        onCycle = { cycles++ },
                        onPay = {},
                    )
                }
            }
        }
    }

    private fun deckLabelContains(text: String) = SemanticsMatcher("label contains $text") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { text in it } == true
    }

    @Test
    fun `a viewer who owes sees Pay on the stack`() {
        show({ listOf(request()) }, viewer = debtor)

        composeTestRule.onNodeWithTag(DECK_TAG).assertExists()
        composeTestRule.onNodeWithContentDescription(payTwenty).assertExists()
        composeTestRule.onNode(deckLabelContains("You owe $20 for Dinner")).assertExists()
    }

    // 24
    @Test
    fun `the creator sees the stack with their status line and no Pay`() {
        show({ listOf(request()) }, viewer = creator)

        composeTestRule.onNodeWithTag(DECK_TAG).assertExists()
        composeTestRule.onAllNodesWithContentDescription(payTwenty).assertCountEquals(0)
        composeTestRule.onNode(deckLabelContains("your request")).assertExists()
    }

    // 24
    @Test
    fun `a viewer who has paid sees the stack with their status line and no Pay`() {
        show({ listOf(request()) }, viewer = payer)

        composeTestRule.onNodeWithTag(DECK_TAG).assertExists()
        composeTestRule.onAllNodesWithContentDescription(payTwenty).assertCountEquals(0)
        composeTestRule.onNode(deckLabelContains("you paid $20")).assertExists()
    }

    // 25
    @Test
    fun `paying the only owed share takes Pay away and leaves the stack in place`() {
        var deck by mutableStateOf(listOf(request()))
        show({ deck }, viewer = debtor)
        composeTestRule.onNodeWithContentDescription(payTwenty).assertExists()
        val before = composeTestRule.onNodeWithTag(DECK_TAG).fetchSemanticsNode().boundsInRoot

        deck = listOf(request(debtorPaid = true))
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithContentDescription(payTwenty).assertCountEquals(0)
        composeTestRule.onNode(deckLabelContains("you paid $20")).assertExists()
        assertEquals(before, composeTestRule.onNodeWithTag(DECK_TAG).fetchSemanticsNode().boundsInRoot)
    }

    // 27
    @Test
    fun `a drag up on the stack neither cycles nor docks it`() {
        show({ listOf(request(), request(id = 8, title = "Lunch")) }, viewer = debtor)
        val before = composeTestRule.onNodeWithTag(DECK_TAG).fetchSemanticsNode().boundsInRoot

        composeTestRule.onNode(deckLabelContains("Dinner")).performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        assertEquals(0, cycles)
        composeTestRule.onNodeWithContentDescription(payTwenty).assertExists()
        assertEquals(before, composeTestRule.onNodeWithTag(DECK_TAG).fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `a swipe down on the stack still cycles the requests`() {
        show({ listOf(request(), request(id = 8, title = "Lunch")) }, viewer = debtor)

        composeTestRule.onNode(deckLabelContains("Dinner")).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertEquals(1, cycles)
    }
}
