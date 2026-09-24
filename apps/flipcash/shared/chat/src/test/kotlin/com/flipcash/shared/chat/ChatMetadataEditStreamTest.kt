package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatIdGenerator
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.delegates.DmChatResolverDelegate
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.utils.network.NetworkConnectivityListener
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

/**
 * The title/picture arm of the event stream: `TitleChanged` and `PictureChanged` are applied
 * unconditionally, unlike the versioned [MetadataUpdate.ViewerStateChanged] handling covered in
 * [ViewerStateStreamTest] — neither carries a version, so there is nothing for the delegate to
 * gate the write on. Delivery is best-effort, by contract; a client that suspects a miss is
 * expected to refetch via `GetChat` rather than relying on the stream to correct itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatMetadataEditStreamTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val chatId = ChatId("aabbccdd")

    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var coordinator: RealChatCoordinator
    private lateinit var testDispatchers: TestDispatchers

    @Before
    fun setUp() {
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
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        metadataDataSource = mockk(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        val feedDelegate = FeedSyncDelegate(
            messagingController = mockk(relaxed = true),
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
            tokenCoordinator = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = stateHolder,
            analytics = mockk(relaxed = true),
            exchange = mockk(relaxed = true),
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
            analytics = mockk(relaxed = true),
            senderResolver = mockk(relaxed = true),
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
            draftStore = mockk<ChatDraftStore>(relaxed = true),
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = testDispatchers,
            groupFeedDelegate = mockk(relaxed = true),
        )
    }

    private suspend fun triggerCollection() {
        coordinator.onUserLoggedIn(mockk(relaxed = true))
    }

    @Test
    fun `a title change from the stream is stored`() = runTest(testDispatchers.dispatcher) {
        tornDown {
            triggerCollection()

            chatUpdatesChannel.send(
                ChatUpdate(
                    chatId = chatId,
                    metadataUpdates = listOf(MetadataUpdate.TitleChanged("New title")),
                ),
            )
            advanceTimeBy(1_000.milliseconds)
            runCurrent()

            coVerify(exactly = 1) { metadataDataSource.updateTitle(chatId, "New title") }
        }
    }

    @Test
    fun `a picture change from the stream is stored`() = runTest(testDispatchers.dispatcher) {
        tornDown {
            triggerCollection()
            val picture = MediaItem(renditions = emptyList())

            chatUpdatesChannel.send(
                ChatUpdate(
                    chatId = chatId,
                    metadataUpdates = listOf(MetadataUpdate.PictureChanged(picture)),
                ),
            )
            advanceTimeBy(1_000.milliseconds)
            runCurrent()

            coVerify(exactly = 1) { metadataDataSource.updatePicture(chatId, picture) }
        }
    }

    @Test
    fun `an update carrying no title or picture change leaves them alone`() =
        runTest(testDispatchers.dispatcher) {
            tornDown {
                triggerCollection()

                chatUpdatesChannel.send(
                    ChatUpdate(
                        chatId = chatId,
                        metadataUpdates = listOf(
                            MetadataUpdate.LastActivityChanged(kotlin.time.Instant.fromEpochSeconds(2_000)),
                        ),
                    ),
                )
                advanceTimeBy(1_000.milliseconds)
                runCurrent()

                coVerify(exactly = 0) { metadataDataSource.updateTitle(any(), any()) }
                coVerify(exactly = 0) { metadataDataSource.updatePicture(any(), any()) }
            }
        }

    /**
     * Runs [block], then tears the coordinator down however it ends.
     *
     * The `finally` is the point. Logging in starts a heartbeat that is a `while (true)` of delays
     * on the coordinator's own scope rather than the test's `backgroundScope`, and `runTest` drains
     * the scheduler once the body returns. A failing assertion that skipped the teardown would
     * leave that loop advancing virtual time with nothing to stop it, hanging the run instead of
     * reporting the failure.
     */
    private suspend fun tornDown(block: suspend () -> Unit) {
        try {
            block()
        } finally {
            coordinator.teardown()
        }
    }
}
