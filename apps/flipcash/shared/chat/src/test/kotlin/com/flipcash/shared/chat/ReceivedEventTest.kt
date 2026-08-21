package com.flipcash.shared.chat

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatIdGenerator
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.delegates.DmChatResolverDelegate
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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
class ReceivedEventTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatId = ChatId("aabbccdd")
    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")

    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private lateinit var analytics: FlipcashAnalyticsService
    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var memberDataSource: ChatMemberDataSource
    private lateinit var messageDataSource: ChatMessageDataSource
    private lateinit var exchange: Exchange
    private lateinit var coordinator: RealChatCoordinator
    private lateinit var testDispatchers: TestDispatchers

    /** Watermark the fake metadata source will report. */
    private var countedThrough: Long = 0L

    @Before
    fun setUp() {
        analytics = mockk(relaxed = true)
        exchange = mockk(relaxed = true)
        every { exchange.rateToUsd(CurrencyCode.USD) } returns Rate(1.0, CurrencyCode.USD)
        every { exchange.rateToUsd(CurrencyCode.CAD) } returns Rate(0.5, CurrencyCode.USD)

        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId

        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns chatUpdatesChannel.receiveAsFlow()
        every { eventStreamingController.isConnected } returns true
        every { eventStreamingController.isStreamActive } returns true

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(any(), any()) } returns
            Result.failure(RuntimeException("not needed"))

        testDispatchers = TestDispatchers(TestCoroutineScheduler())

        val stateHolder = ChatStateHolder()
        memberDataSource = mockk(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        messageDataSource = mockk(relaxed = true)

        metadataDataSource = mockk(relaxed = true)
        coEvery { metadataDataSource.getAnalyticsCountedThrough(any()) } answers { countedThrough }
        coEvery { metadataDataSource.advanceAnalyticsCountedThrough(any(), any()) } answers {
            countedThrough = maxOf(countedThrough, secondArg())
        }
        coEvery { metadataDataSource.getChatType(chatId) } returns ChatType.TIP_DM

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
            tokenCoordinator = mockk<TokenCoordinator>(relaxed = true),
            userManager = userManager,
            stateHolder = stateHolder,
            analytics = analytics,
            exchange = exchange,
        )

        val messagingDelegate = MessagingDelegate(
            chatController = chatController,
            messagingController = messagingController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            notificationManager = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = stateHolder,
            analytics = analytics,
        )

        val dmChatResolverDelegate = DmChatResolverDelegate(
            chatIdGenerator = ChatIdGenerator(),
            userManager = userManager,
            contactDataSource = mockk<ContactDataSource>(relaxed = true),
            memberDataSource = memberDataSource,
        )

        coordinator = RealChatCoordinator(
            feedDelegate = feedDelegate,
            eventStreamDelegate = eventStreamDelegate,
            dmChatResolverDelegate = dmChatResolverDelegate,
            messagingDelegate = messagingDelegate,
            stateHolder = stateHolder,
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = testDispatchers,
        )
    }

    private fun tipMessage(
        messageId: Long,
        senderId: List<Byte>?,
        amount: Fiat = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD),
    ) = ChatMessage(
        messageId = messageId,
        senderId = senderId,
        content = listOf(
            MessageContent.Cash(
                intentId = listOf(0),
                amount = amount,
                mint = mint,
                action = MessageContent.Cash.Action.TIPPED,
            )
        ),
        timestamp = Instant.fromEpochSeconds(1000),
        unreadSeq = 0,
    )

    private fun textMessage(messageId: Long, senderId: List<Byte>?) = ChatMessage(
        messageId = messageId,
        senderId = senderId,
        content = listOf(MessageContent.Text("hello")),
        timestamp = Instant.fromEpochSeconds(1000),
        unreadSeq = 0,
    )

    // newMessages is deprecated in favour of `events`, but applyUpdate still falls
    // back to it and every existing chat test builds updates this way. Matching the
    // existing harness keeps these tests readable next to their neighbours.
    private fun chatUpdate(vararg messages: ChatMessage) = ChatUpdate(
        chatId = chatId,
        newMessages = messages.toList(),
        pointerUpdates = emptyList(),
        typingNotifications = emptyList(),
        metadataUpdates = emptyList(),
    )

    private suspend fun TestScope.deliver(vararg messages: ChatMessage) {
        chatUpdatesChannel.send(chatUpdate(*messages))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
    }

    @Test
    fun `crossing inbound messages emits one event each`() = runTest(testDispatchers.dispatcher) {
        coEvery { memberDataSource.getSelfReadPointer(chatId, selfId) } returns 0L
        coEvery {
            messageDataSource.getInboundMessagesInRange(chatId, selfId, 0L, 3L)
        } returns listOf(
            textMessage(messageId = 1L, senderId = otherId),
            textMessage(messageId = 2L, senderId = otherId),
            textMessage(messageId = 3L, senderId = otherId),
        )

        coordinator.advanceReadPointer(chatId, 3L)

        coVerify(exactly = 3) { analytics.messageReceived(ChatType.TIP_DM) }
    }

    @Test
    fun `a crossed tip emits Tip Received and not Message Received`() =
        runTest(testDispatchers.dispatcher) {
            coEvery { memberDataSource.getSelfReadPointer(chatId, selfId) } returns 0L
            coEvery {
                messageDataSource.getInboundMessagesInRange(chatId, selfId, 0L, 1L)
            } returns listOf(tipMessage(messageId = 1L, senderId = otherId))

            coordinator.advanceReadPointer(chatId, 1L)

            coVerify(exactly = 1) { analytics.tipReceived(ChatType.TIP_DM, any(), any()) }
            coVerify(exactly = 0) { analytics.messageReceived(any()) }
        }

    @Test
    fun `a backward advance emits nothing`() = runTest(testDispatchers.dispatcher) {
        coEvery { memberDataSource.getSelfReadPointer(chatId, selfId) } returns 10L

        coordinator.advanceReadPointer(chatId, 5L)

        coVerify(exactly = 0) { analytics.messageReceived(any()) }
        coVerify(exactly = 0) { analytics.tipReceived(any(), any(), any()) }
    }

    @Test
    fun `a repeated advance to the same pointer emits nothing`() =
        runTest(testDispatchers.dispatcher) {
            coEvery { memberDataSource.getSelfReadPointer(chatId, selfId) } returns 3L

            coordinator.advanceReadPointer(chatId, 3L)

            coVerify(exactly = 0) { analytics.messageReceived(any()) }
        }

    @Test
    fun `outbound messages in the range are not reported`() =
        runTest(testDispatchers.dispatcher) {
            coEvery { memberDataSource.getSelfReadPointer(chatId, selfId) } returns 0L
            // The DAO filters by sender, so an all-outbound range comes back empty.
            coEvery {
                messageDataSource.getInboundMessagesInRange(chatId, selfId, 0L, 2L)
            } returns emptyList()

            coordinator.advanceReadPointer(chatId, 2L)

            coVerify(exactly = 0) { analytics.messageReceived(any()) }
        }
}
