package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.tokens.TokenCoordinator
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
import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.SignalStrength
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The event stream is a long-lived bidirectional gRPC stream, and background delivery does not
 * depend on it — a chat that arrives while the app is away comes in over FCM
 * ([com.flipcash.app.notifications.NotificationService]). So the stream belongs to the foreground,
 * which is what [RealChatCoordinator.onStop] closing it says.
 *
 * The network-reconnect collector lives on the session scope rather than the foreground one, and
 * it reopened the stream behind that close: a Bugsnag timeline had `onStop` at 17:09:04 and the
 * stream back up at 17:09:11, feed sync and all, with the app in the background.
 *
 * The same collector also fired every few seconds in the foreground, because it de-duplicated the
 * whole [NetworkState] — signal strength and connection type included — rather than the one field
 * it acts on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatCoordinatorLifecycleStreamTest {

    private class FakeNetworkObserver : NetworkConnectivityListener {
        val mutableState = MutableStateFlow(NetworkState.Default)
        override val state: StateFlow<NetworkState> = mutableState.asStateFlow()
        override val isConnected: Boolean get() = mutableState.value.connected
        override val type: ConnectionType get() = mutableState.value.type
    }

    private lateinit var eventStreamingController: EventStreamingController
    private var opens = 0
    private lateinit var networkObserver: FakeNetworkObserver
    private lateinit var coordinator: RealChatCoordinator
    private lateinit var testDispatchers: TestDispatchers

    @Before
    fun setUp() {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns listOf<Byte>(1, 2, 3)

        eventStreamingController = mockk(relaxed = true)
        every { eventStreamingController.chatUpdates } returns
            Channel<ChatUpdate>(Channel.UNLIMITED).receiveAsFlow()
        // Never "already connected", so every reopen attempt reaches the controller and can be
        // counted. isStreamActive keeps the heartbeat a no-op supervisor for the whole test.
        every { eventStreamingController.isConnected } returns false
        opens = 0
        every { eventStreamingController.open(any()) } answers { opens++; true }
        every { eventStreamingController.isStreamActive } returns true
        every { eventStreamingController.streamFailures } returns emptyFlow()

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(any(), any()) } returns
            Result.failure(RuntimeException("not needed"))

        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val stateHolder = ChatStateHolder()

        networkObserver = FakeNetworkObserver()
        testDispatchers = TestDispatchers(TestCoroutineScheduler())

        coordinator = RealChatCoordinator(
            feedDelegate = FeedSyncDelegate(
                messagingController = mockk(relaxed = true),
                chatController = chatController,
                metadataDataSource = metadataDataSource,
                messageDataSource = messageDataSource,
                memberDataSource = memberDataSource,
                stateHolder = stateHolder,
                userManager = userManager,
            ),
            eventStreamDelegate = EventStreamDelegate(
                eventStreamingController = eventStreamingController,
                messagingController = messagingController,
                metadataDataSource = metadataDataSource,
                messageDataSource = messageDataSource,
                memberDataSource = memberDataSource,
                tokenCoordinator = mockk<TokenCoordinator>(relaxed = true),
                userManager = userManager,
                stateHolder = stateHolder,
                analytics = mockk(relaxed = true),
                exchange = mockk(relaxed = true),
            ),
            dmChatResolverDelegate = DmChatResolverDelegate(
                chatIdGenerator = ChatIdGenerator(),
                userManager = userManager,
                contactDataSource = mockk<ContactDataSource>(relaxed = true),
                memberDataSource = memberDataSource,
            ),
            messagingDelegate = MessagingDelegate(
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
            ),
            stateHolder = stateHolder,
            draftStore = mockk<ChatDraftStore>(relaxed = true),
            userManager = userManager,
            networkObserver = networkObserver,
            dispatchers = testDispatchers,
            groupFeedDelegate = mockk(relaxed = true),
            reactionsDelegate = mockk(relaxed = true),
        )
    }

    // Every test tears the coordinator down BEFORE it asserts, and steps with
    // runCurrent()/advanceTimeBy rather than advanceUntilIdle. The heartbeat started in
    // onStart is an unbounded timed loop: "until idle" never arrives, and an assertion that
    // threw before teardown would leave runTest's own drain spinning on virtual time rather
    // than reporting the failure.
    private fun connected(strength: SignalStrength, type: ConnectionType = ConnectionType.Wifi) {
        networkObserver.mutableState.value = NetworkState(true, strength, type)
    }

    private fun disconnected() {
        networkObserver.mutableState.value = NetworkState.Default
    }

    @Test
    fun `signal strength changes while connected do not reopen the stream`() =
        runTest(testDispatchers.dispatcher) {
            coordinator.onUserLoggedIn(mockk(relaxed = true))
            coordinator.onStart(mockk(relaxed = true))
            runCurrent()

            connected(SignalStrength.Good)
            advanceTimeBy(2.seconds)
            runCurrent()

            // What a cellular radio reports every few seconds on a connection that never dropped.
            // Spaced out rather than set back to back: the observer's state is a conflated
            // StateFlow, so wobbles applied in the same instant collapse into one before the
            // collector ever sees them, and the churn under test never happens.
            listOf(
                SignalStrength.Great to ConnectionType.Wifi,
                SignalStrength.Strong to ConnectionType.Cellular,
                SignalStrength.Poor to ConnectionType.Cellular,
                SignalStrength.Good to ConnectionType.Wifi,
            ).forEach { (strength, type) ->
                connected(strength, type)
                advanceTimeBy(3.seconds)
                runCurrent()
            }

            coordinator.teardown()

            // Login, foreground, and the one real connect.
            assertEquals(3, opens)
        }

    @Test
    fun `a reconnect in the background does not reopen the stream`() =
        runTest(testDispatchers.dispatcher) {
            coordinator.onUserLoggedIn(mockk(relaxed = true))
            coordinator.onStart(mockk(relaxed = true))
            connected(SignalStrength.Good)
            advanceTimeBy(2.seconds)
            runCurrent()

            coordinator.onStop(mockk(relaxed = true))
            runCurrent()

            disconnected()
            advanceTimeBy(2.seconds)
            connected(SignalStrength.Good)
            advanceTimeBy(10.seconds)
            runCurrent()

            coordinator.teardown()

            // Still only login, foreground, and the connect from before backgrounding.
            assertEquals(3, opens)
        }

    @Test
    fun `a reconnect in the foreground reopens the stream`() =
        runTest(testDispatchers.dispatcher) {
            coordinator.onUserLoggedIn(mockk(relaxed = true))
            coordinator.onStart(mockk(relaxed = true))
            connected(SignalStrength.Good)
            advanceTimeBy(2.seconds)
            runCurrent()

            disconnected()
            advanceTimeBy(2.seconds)
            connected(SignalStrength.Good)
            advanceTimeBy(2.seconds)
            runCurrent()

            coordinator.teardown()

            assertEquals(4, opens)
        }

    /** Backgrounding and then returning still reopens — the gate is the lifecycle, not a latch. */
    @Test
    fun `returning to the foreground reopens the stream`() =
        runTest(testDispatchers.dispatcher) {
            coordinator.onUserLoggedIn(mockk(relaxed = true))
            coordinator.onStart(mockk(relaxed = true))
            connected(SignalStrength.Good)
            advanceTimeBy(2.seconds)
            runCurrent()

            coordinator.onStop(mockk(relaxed = true))
            disconnected()
            advanceTimeBy(2.seconds)
            connected(SignalStrength.Good)
            advanceTimeBy(10.seconds)
            runCurrent()

            coordinator.onStart(mockk(relaxed = true))
            runCurrent()

            coordinator.teardown()

            assertEquals(4, opens)
        }
}
