package com.flipcash.app.tipping.internal

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A payment preview restates the sent amount in the display currency, which needs both currencies'
 * rates. Rates stream in batches, so the display currency's can land first; the preview must redraw
 * when the sent currency's follows, though the preferred rate has not changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelRatesTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val cad = Rate(fx = 1.4, currency = CurrencyCode.CAD)
    private val gbp = Rate(fx = 0.8, currency = CurrencyCode.GBP)

    private val chat = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(ByteArray(16) { 3 }.toList()),
            type = ChatType.GROUP,
            members = emptyList(),
            lastMessage = ChatMessage(
                messageId = 1,
                senderId = null,
                content = listOf(
                    MessageContent.Cash(
                        intentId = ByteArray(32) { 1 }.toList(),
                        amount = Fiat(5.0, CurrencyCode.GBP),
                        mint = Mint.usdf,
                    )
                ),
                timestamp = Instant.fromEpochSeconds(1_000),
                unreadSeq = 0,
            ),
            lastActivity = Instant.fromEpochSeconds(1_000),
            title = "Ballers",
            latestEventSequence = 0,
        ),
        unreadCount = 0,
    )

    @Test
    fun `a payment preview redraws when the sent currency's rate lands after the display rate`() =
        runTest(mainCoroutineRule.dispatcher) {
            val preferred = MutableStateFlow(cad)
            val rates = MutableStateFlow(mapOf(CurrencyCode.CAD to cad))
            val exchange = mockk<Exchange>(relaxed = true).also {
                every { it.preferredRate } answers { preferred.value }
                every { it.rates() } answers { rates.value }
                every { it.observePreferredRate() } returns preferred
                every { it.observeRates() } returns rates
            }
            val feeds = MutableStateFlow(ChatFeeds(main = listOf(chat), archived = emptyList()))
            val chatCoordinator = mockk<ChatCoordinator>(relaxed = true).also {
                every { it.feedWithArchived(*anyVararg()) } returns feeds
                every { it.currentFeedWithArchived(*anyVararg()) } returns null
                every { it.observeSenderProfiles() } returns flowOf(emptyMap())
            }
            val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true).also {
                every { it.tokens } returns flowOf(emptyList())
                every { it.cachedTokens() } returns emptyList()
            }
            val vm = ChatsViewModel(
                exchange = exchange,
                chatCoordinator = chatCoordinator,
                userManager = mockk<UserManager>(relaxed = true),
                tokenCoordinator = tokenCoordinator,
                resources = FakeResourceHelper(R::class.java),
                dispatchers = TestDispatchers(testScheduler),
                archiveStore = ChatArchiveStore.None,
                lobbyRequests = mockk(relaxed = true),
                hostLobbies = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
                paymentRequests = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
            )

            val states = mutableListOf<ChatsViewModel.State>()
            backgroundScope.launch { vm.stateFlow.collect { states += it } }
            advanceUntilIdle()

            fun preview() = states.last().chats.dataOrNull?.single()?.lastMessagePreview

            // GBP has no rate yet, so the amount can only read as it was sent.
            assertEquals(Fiat(5.0, CurrencyCode.GBP).formatted(), preview())

            rates.value = rates.value + (CurrencyCode.GBP to gbp)
            advanceUntilIdle()

            // 5 GBP / 0.8 = 6.25 USD, * 1.4 = 8.75 CAD.
            assertEquals(Fiat(8.75, CurrencyCode.CAD).formatted(), preview())
        }
}
