package com.flipcash.shared.chat

import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.utils.network.NetworkConnectivityListener
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatCoordinatorEagerBalanceTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatId = ChatId("aabbccdd")
    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")

    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private lateinit var tokenCoordinator: TokenCoordinator
    private lateinit var coordinator: RealChatCoordinator
    private lateinit var testDispatchers: TestDispatchers

    @Before
    fun setUp() {
        tokenCoordinator = mockk(relaxed = true)
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId
        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns chatUpdatesChannel.receiveAsFlow()
        every { eventStreamingController.isConnected } returns true
        every { eventStreamingController.isStreamActive } returns true

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed() } returns Result.failure(RuntimeException("not needed"))

        testDispatchers = TestDispatchers(TestCoroutineScheduler())

        val stateHolder = ChatStateHolder()
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        val feedDelegate = FeedSyncDelegate(
            chatController = chatController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            stateHolder = stateHolder,
            userManager = userManager,
        )

        val eventStreamDelegate = EventStreamDelegate(
            eventStreamingController = eventStreamingController,
            messagingController = messagingController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            tokenCoordinator = tokenCoordinator,
            userManager = userManager,
            stateHolder = stateHolder,
        )

        val messagingDelegate = MessagingDelegate(
            chatController = chatController,
            messagingController = messagingController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            contactDataSource = mockk<ContactDataSource>(relaxed = true),
            notificationManager = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = stateHolder,
        )

        coordinator = RealChatCoordinator(
            feedDelegate = feedDelegate,
            eventStreamDelegate = eventStreamDelegate,
            messagingDelegate = messagingDelegate,
            stateHolder = stateHolder,
            userManager = userManager,
            featureFlags = mockk<FeatureFlagController>(relaxed = true),
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = testDispatchers,
        )
    }

    private fun cashMessage(
        senderId: List<Byte>?,
        amount: Fiat = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD),
        mint: Mint = this.mint,
    ) = ChatMessage(
        messageId = 1L,
        senderId = senderId,
        content = listOf(MessageContent.Cash(
            intentId = listOf(0),
            amount = amount,
            mint = mint,
        )),
        timestamp = Instant.fromEpochSeconds(1000),
        unreadSeq = 0,
    )

    private fun textMessage(senderId: List<Byte>?) = ChatMessage(
        messageId = 2L,
        senderId = senderId,
        content = listOf(MessageContent.Text("hello")),
        timestamp = Instant.fromEpochSeconds(1000),
        unreadSeq = 0,
    )

    private fun chatUpdate(vararg messages: ChatMessage) = ChatUpdate(
        chatId = chatId,
        newMessages = messages.toList(),
        pointerUpdates = emptyList(),
        typingNotifications = emptyList(),
        metadataUpdates = emptyList(),
    )

    private suspend fun triggerCollection() {
        coordinator.onUserLoggedIn(mockk(relaxed = true))
    }

    @Test
    fun `incoming cash message triggers tokenCoordinator add`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        val amount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)
        chatUpdatesChannel.send(chatUpdate(cashMessage(senderId = otherId, amount = amount)))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify(exactly = 1) { tokenCoordinator.add(mint, amount) }
        coordinator.reset()
    }

    @Test
    fun `self-sent cash message does not trigger tokenCoordinator add`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        chatUpdatesChannel.send(chatUpdate(cashMessage(senderId = selfId)))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify(exactly = 0) { tokenCoordinator.add(any<Mint>(), any()) }
        coordinator.reset()
    }

    @Test
    fun `text message does not trigger tokenCoordinator add`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        chatUpdatesChannel.send(chatUpdate(textMessage(senderId = otherId)))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify(exactly = 0) { tokenCoordinator.add(any<Mint>(), any()) }
        coordinator.reset()
    }

    @Test
    fun `multiple cash messages in one update each trigger add`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        val mintB = Mint("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBbbbbbbbbbbb")
        val amount1 = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD)
        val amount2 = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val msg1 = cashMessage(senderId = otherId, amount = amount1, mint = mint)
        val msg2 = cashMessage(senderId = otherId, amount = amount2, mint = mintB).copy(messageId = 3L)

        chatUpdatesChannel.send(chatUpdate(msg1, msg2))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify(exactly = 1) { tokenCoordinator.add(mint, amount1) }
        coVerify(exactly = 1) { tokenCoordinator.add(mintB, amount2) }
        coordinator.reset()
    }

    @Test
    fun `mixed self and incoming messages only triggers add for incoming`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        val incoming = cashMessage(senderId = otherId)
        val outgoing = cashMessage(senderId = selfId).copy(messageId = 3L)

        chatUpdatesChannel.send(chatUpdate(incoming, outgoing))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify(exactly = 1) { tokenCoordinator.add(any<Mint>(), any()) }
        coordinator.reset()
    }
}
