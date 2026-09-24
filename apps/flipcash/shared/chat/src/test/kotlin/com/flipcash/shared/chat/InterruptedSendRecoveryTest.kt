package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatUpdate
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
import kotlinx.coroutines.delay
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * A send the app was killed in the middle of has to be marked failed before the session's first
 * refresh, or the refresh deletes it.
 *
 * Nothing in-process survives the kill: a send is marked failed from the `onFailure` of the
 * coroutine that issued it, so the row is left `SENDING` on disk. The first refresh that carries a
 * self-authored message runs `deleteAllPending`, which is scoped to `SENDING` — so the sweep either
 * runs first and makes the row durable, or runs second and finds nothing. There is no third
 * outcome, which is why the order is worth a test rather than a comment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InterruptedSendRecoveryTest {

    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private lateinit var coordinator: RealChatCoordinator
    private lateinit var messageDataSource: ChatMessageDataSource
    private lateinit var testDispatchers: TestDispatchers

    /** Set when the sweep returns; read by the feed fetch to report what it raced. */
    private var sweepFinished = false
    private var feedFetchSawFinishedSweep: Boolean? = null

    @Before
    fun setUp() {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns listOf<Byte>(1, 2, 3)

        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns chatUpdatesChannel.receiveAsFlow()
        every { eventStreamingController.isConnected } returns true
        every { eventStreamingController.isStreamActive } returns true

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(any(), any()) } coAnswers {
            feedFetchSawFinishedSweep = sweepFinished
            Result.failure(RuntimeException("feed contents are not what this test is about"))
        }

        testDispatchers = TestDispatchers(TestCoroutineScheduler())

        val stateHolder = ChatStateHolder()
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        messageDataSource = mockk(relaxed = true)
        // The sweep is a Room write, so it is modelled with a suspension point. Without one the
        // test cannot tell the fix from the bug: the refresh is a `launch`, so on a single-threaded
        // test dispatcher it could not start before a sweep that never suspends had already
        // returned, and a sweep placed after it would still appear to have gone first.
        coEvery { messageDataSource.failInterruptedSends() } coAnswers {
            delay(1)
            sweepFinished = true
        }

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

    /**
     * Signs in, lets the login work settle, and tears the coordinator down however [block] ends.
     *
     * The `finally` is load-bearing, for the reason the other coordinator tests give: logging in
     * starts a heartbeat that is a `while (true)` of delays on the coordinator's own scope, which
     * is not [TestScope.backgroundScope] and so outlives the body. [runTest] drains the scheduler
     * once the body returns, so a failed assertion that skipped the teardown would leave that loop
     * spinning virtual time and bury the failure in a run that never ends.
     *
     * The advance is bounded for the same reason — `advanceUntilIdle` would never come back.
     */
    private suspend fun TestScope.loggedIn(block: () -> Unit) {
        try {
            coordinator.onUserLoggedIn(mockk(relaxed = true))
            advanceTimeBy(10.milliseconds)
            runCurrent()
            block()
        } finally {
            coordinator.teardown()
        }
    }

    @Test
    fun `login sweeps interrupted sends before it fetches the feed`() =
        runTest(testDispatchers.dispatcher) {
            loggedIn {
                assertTrue(
                    feedFetchSawFinishedSweep == true,
                    "The feed fetch ran while the sweep was still in flight, so a refresh " +
                        "carrying a self-authored message could delete a SENDING row before it " +
                        "had been marked failed.",
                )
            }
        }

    @Test
    fun `the sweep runs once per login`() = runTest(testDispatchers.dispatcher) {
        loggedIn {
            coVerify(exactly = 1) { messageDataSource.failInterruptedSends() }
        }
    }
}
