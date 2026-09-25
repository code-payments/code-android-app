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
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
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
import kotlin.time.Instant

/**
 * The viewer-state arm of the event stream: a `ViewerStateChanged` is stored rather than dropped.
 *
 * These assert the hand-off only. Which of two viewer states wins is decided by the version gate in
 * the DAO, and is covered where that gate lives
 * ([com.flipcash.app.persistence.dao.ChatMetadataDaoTest]) — the delegate hands every update over
 * and lets the version sort them out, because it cannot know what else is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewerStateStreamTest {

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
            reactionsDelegate = mockk(relaxed = true),
        )
    }

    private suspend fun triggerCollection() {
        coordinator.onUserLoggedIn(mockk(relaxed = true))
    }

    private fun viewerStateUpdate(viewerState: ViewerState) = ChatUpdate(
        chatId = chatId,
        metadataUpdates = listOf(MetadataUpdate.ViewerStateChanged(viewerState)),
    )

    @Test
    fun `a timed mute from the stream is stored`() = runTest(testDispatchers.dispatcher) {
        tornDown {
            triggerCollection()
            val viewerState = ViewerState(
                mute = MuteState.Until(Instant.fromEpochSeconds(2_000)),
                version = 4,
            )

            chatUpdatesChannel.send(viewerStateUpdate(viewerState))
            advanceTimeBy(1_000.milliseconds)
            runCurrent()

            coVerify(exactly = 1) { metadataDataSource.updateViewerState(chatId, viewerState) }
        }
    }

    @Test
    fun `an indefinite mute from the stream is stored`() = runTest(testDispatchers.dispatcher) {
        tornDown {
            triggerCollection()
            val viewerState = ViewerState(mute = MuteState.Forever, version = 7)

            chatUpdatesChannel.send(viewerStateUpdate(viewerState))
            advanceTimeBy(1_000.milliseconds)
            runCurrent()

            coVerify(exactly = 1) { metadataDataSource.updateViewerState(chatId, viewerState) }
        }
    }

    /**
     * An unmute arrives as a viewer state carrying no mute, not as an absent update: the version it
     * carries is what makes it beat the mute it replaces, so it has to be written like any other.
     */
    @Test
    fun `an unmute from the stream is stored at its own version`() =
        runTest(testDispatchers.dispatcher) {
            tornDown {
                triggerCollection()
                val viewerState = ViewerState(mute = null, version = 8)

                chatUpdatesChannel.send(viewerStateUpdate(viewerState))
                advanceTimeBy(1_000.milliseconds)
                runCurrent()

                coVerify(exactly = 1) { metadataDataSource.updateViewerState(chatId, viewerState) }
            }
        }

    @Test
    fun `an update carrying no viewer state leaves the stored one alone`() =
        runTest(testDispatchers.dispatcher) {
            tornDown {
                triggerCollection()

                chatUpdatesChannel.send(
                    ChatUpdate(
                        chatId = chatId,
                        metadataUpdates = listOf(
                            MetadataUpdate.LastActivityChanged(Instant.fromEpochSeconds(2_000)),
                        ),
                    ),
                )
                advanceTimeBy(1_000.milliseconds)
                runCurrent()

                coVerify(exactly = 0) { metadataDataSource.updateViewerState(any(), any()) }
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
