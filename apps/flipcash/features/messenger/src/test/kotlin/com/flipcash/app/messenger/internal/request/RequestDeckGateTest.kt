package com.flipcash.app.messenger.internal.request

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * The deck under a group chat's title, by flag. With Group Money on a collection is reached only
 * from its bubble, so the chat gets no deck: it neither points the deck at the chat nor reads it,
 * even when the deck holds requests and was left expanded.
 */
@RunWith(RobolectricTestRunner::class)
class RequestDeckGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val chat = ChatId(ByteArray(32) { 3 })
    private val creator: ID = List(16) { 1 }
    private val at = Instant.fromEpochSeconds(500)

    private val split = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chat,
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(500 + 86_400L),
                shares = listOf(Share(creator, Fiat(20.0, CurrencyCode.USD), null)),
            ),
        ),
    )

    private val collection = split.copy(
        id = List(16) { 7 },
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = chat,
            collection = PaymentCollection(
                text = PaymentRequestText.Plain("Trip", ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(500 + 86_400L),
                amount = CollectionAmount.Fixed(Fiat(10.0, CurrencyCode.USD)),
            ),
        ),
    )

    private val viewModel = mockk<ChatRequestsViewModel>(relaxed = true) {
        every { deck } returns MutableStateFlow(listOf(split, collection))
        every { expanded } returns MutableStateFlow(true)
        every { now } returns MutableStateFlow(at)
    }

    private fun deckUnder(shell: NavBarShell): RequestDeck? {
        var result: RequestDeck? = null
        var composed = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                result = rememberRequestDeck(viewModel = viewModel, chatId = chat)
                composed = true
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(composed)
        return result
    }

    @Test
    fun `flag off, the chat attaches the deck and draws what it holds`() {
        val deck = deckUnder(NavBarShell.Classic)

        // A collection has no deck title, so only the split draws.
        assertEquals(listOf(split), deck?.cards)
        assertEquals(at, deck?.now)
        assertEquals(true, deck?.expanded)
        verify(exactly = 1) { viewModel.attach(chat) }
    }

    @Test
    fun `flag on, the chat has no deck and leaves the view model's deck alone`() {
        val deck = deckUnder(NavBarShell.GroupMoney)

        assertNull(deck)
        verify(exactly = 0) { viewModel.attach(any()) }
        verify(exactly = 0) { viewModel.deck }
        verify(exactly = 0) { viewModel.expanded }
    }
}
