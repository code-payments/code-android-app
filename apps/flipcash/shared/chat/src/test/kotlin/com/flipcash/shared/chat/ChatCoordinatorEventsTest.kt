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
import com.flipcash.services.models.chat.ChatEvent
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMutation
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ReactionUpdate
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatCoordinatorEventsTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatId = ChatId("aabbccdd")

    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var messageDataSource: ChatMessageDataSource
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
        coEvery { chatController.getDmChatFeed() } returns Result.failure(RuntimeException("not needed"))

        metadataDataSource = mockk(relaxed = true)
        messageDataSource = mockk(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)

        testDispatchers = TestDispatchers(TestCoroutineScheduler())

        val stateHolder = ChatStateHolder()

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

    private fun textMessage(
        id: Long,
        senderId: List<Byte>? = otherId,
        eventSequence: Long = 0,
    ) = ChatMessage(
        messageId = id,
        senderId = senderId,
        content = listOf(MessageContent.Text("msg-$id")),
        timestamp = Instant.fromEpochSeconds(1000 + id),
        unreadSeq = 0,
        eventSequence = eventSequence,
    )

    private fun chatEvent(sequence: Long, message: ChatMessage) = ChatEvent(
        sequence = sequence,
        count = 1,
        ts = message.timestamp,
        mutations = listOf(ChatMutation.MessageSent(message)),
    )

    private suspend fun triggerCollection() {
        coordinator.onUserLoggedIn(mockk(relaxed = true))
    }

    // region Events vs newMessages

    @Test
    fun `events are preferred over deprecated newMessages`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        val eventMsg = textMessage(id = 1, eventSequence = 1)
        val deprecatedMsg = textMessage(id = 99)

        @Suppress("DEPRECATION")
        val update = ChatUpdate(
            chatId = chatId,
            newMessages = listOf(deprecatedMsg),
            events = listOf(chatEvent(1, eventMsg)),
        )
        chatUpdatesChannel.send(update)
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        // Should upsert the event message, not the deprecated one
        coVerify {
            messageDataSource.upsert(chatId, match { messages ->
                messages.size == 1 && messages[0].messageId == 1L
            })
        }
        coordinator.reset()
    }

    @Test
    fun `falls back to newMessages when events is empty`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        val msg = textMessage(id = 42)
        @Suppress("DEPRECATION")
        val update = ChatUpdate(
            chatId = chatId,
            newMessages = listOf(msg),
            events = emptyList(),
        )
        chatUpdatesChannel.send(update)
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify {
            messageDataSource.upsert(chatId, match { messages ->
                messages.size == 1 && messages[0].messageId == 42L
            })
        }
        coordinator.reset()
    }

    @Test
    fun `multiple events are flattened and deduped by messageId`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        val msg1 = textMessage(id = 1, eventSequence = 1)
        val msg1Edited = textMessage(id = 1, eventSequence = 2) // same messageId, higher sequence
        val msg2 = textMessage(id = 2, eventSequence = 3)

        val update = ChatUpdate(
            chatId = chatId,
            events = listOf(
                chatEvent(1, msg1),
                chatEvent(2, msg1Edited),
                chatEvent(3, msg2),
            ),
        )
        chatUpdatesChannel.send(update)
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        // Should have 2 unique messages (deduped by messageId, taking first by sorted eventSequence)
        coVerify {
            messageDataSource.upsert(chatId, match { messages ->
                messages.size == 2
            })
        }
        coordinator.reset()
    }

    // endregion

    // region Sequence advancement with gaps

    @Test
    fun `contiguous events advance sequence cursor`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 0L

        val update = ChatUpdate(
            chatId = chatId,
            events = listOf(
                chatEvent(1, textMessage(id = 1, eventSequence = 1)),
                chatEvent(2, textMessage(id = 2, eventSequence = 2)),
            ),
        )
        chatUpdatesChannel.send(update)
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify { metadataDataSource.updateLatestEventSequence(chatId, 2L) }
        coordinator.reset()
    }

    @Test
    fun `gap in events does not advance cursor past gap`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 0L

        // Send seq 1, then seq 3 (gap at 2)
        val update1 = ChatUpdate(
            chatId = chatId,
            events = listOf(chatEvent(1, textMessage(id = 1, eventSequence = 1))),
        )
        chatUpdatesChannel.send(update1)
        advanceTimeBy(500.milliseconds)
        runCurrent()

        val update2 = ChatUpdate(
            chatId = chatId,
            events = listOf(chatEvent(3, textMessage(id = 3, eventSequence = 3))),
        )
        chatUpdatesChannel.send(update2)
        advanceTimeBy(500.milliseconds)
        runCurrent()

        // Cursor should advance to 1 (contiguous), not 3
        coVerify { metadataDataSource.updateLatestEventSequence(chatId, 1L) }
        coVerify(exactly = 0) { metadataDataSource.updateLatestEventSequence(chatId, 3L) }
        coordinator.reset()
    }

    @Test
    fun `late event fills gap and advances cursor`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 0L

        // Send 1, then 3 (gap), then 2 (fills gap)
        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            events = listOf(chatEvent(1, textMessage(id = 1, eventSequence = 1))),
        ))
        advanceTimeBy(100.milliseconds)
        runCurrent()

        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            events = listOf(chatEvent(3, textMessage(id = 3, eventSequence = 3))),
        ))
        advanceTimeBy(100.milliseconds)
        runCurrent()

        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            events = listOf(chatEvent(2, textMessage(id = 2, eventSequence = 2))),
        ))
        advanceTimeBy(100.milliseconds)
        runCurrent()

        // After filling the gap, cursor should advance to 3
        coVerify { metadataDataSource.updateLatestEventSequence(chatId, 3L) }
        coordinator.reset()
    }

    // endregion

    // region Reaction overlays

    @Test
    fun `reaction update is applied to in-memory overlay`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        val update = ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDE00"),
                    actor = otherId,
                    action = ReactionUpdate.Action.ADDED,
                    count = 1,
                    sequence = 1,
                    reactedAt = Instant.fromEpochSeconds(1000),
                ),
            ),
        )
        chatUpdatesChannel.send(update)
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        val state = coordinator.state.value
        val overlay = state.reactionOverlays[chatId]
        assertNotNull(overlay)
        val summary = overlay[1L]
        assertNotNull(summary)
        assertEquals(1, summary.reactions.size)
        assertEquals("\uD83D\uDE00", summary.reactions[0].emoji.value)
        assertEquals(1L, summary.reactions[0].count)
        coordinator.reset()
    }

    @Test
    fun `reaction LWW guard rejects stale updates`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        // First update: count=3, sequence=5
        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDC4D"),
                    actor = otherId,
                    action = ReactionUpdate.Action.ADDED,
                    count = 3,
                    sequence = 5,
                    reactedAt = Instant.fromEpochSeconds(1000),
                ),
            ),
        ))
        advanceTimeBy(500.milliseconds)
        runCurrent()

        // Stale update: count=1, sequence=2 (older)
        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDC4D"),
                    actor = otherId,
                    action = ReactionUpdate.Action.REMOVED,
                    count = 1,
                    sequence = 2, // older than 5
                    reactedAt = Instant.fromEpochSeconds(500),
                ),
            ),
        ))
        advanceTimeBy(500.milliseconds)
        runCurrent()

        val reactions = coordinator.state.value.reactionOverlays[chatId]?.get(1L)?.reactions
        assertNotNull(reactions)
        assertEquals(1, reactions.size)
        assertEquals(3L, reactions[0].count) // stayed at 3, stale update rejected
        assertEquals(5L, reactions[0].sequence)
        coordinator.reset()
    }

    @Test
    fun `reaction with count zero is pruned`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        // Add reaction
        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDE00"),
                    actor = otherId,
                    action = ReactionUpdate.Action.ADDED,
                    count = 1,
                    sequence = 1,
                    reactedAt = Instant.fromEpochSeconds(1000),
                ),
            ),
        ))
        advanceTimeBy(500.milliseconds)
        runCurrent()

        // Remove reaction (count=0)
        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDE00"),
                    actor = otherId,
                    action = ReactionUpdate.Action.REMOVED,
                    count = 0,
                    sequence = 2,
                    reactedAt = Instant.fromEpochSeconds(2000),
                ),
            ),
        ))
        advanceTimeBy(500.milliseconds)
        runCurrent()

        val reactions = coordinator.state.value.reactionOverlays[chatId]?.get(1L)?.reactions
        assertNotNull(reactions)
        assertTrue(reactions.isEmpty())
        coordinator.reset()
    }

    @Test
    fun `multiple emoji reactions on same message`() = runTest(testDispatchers.dispatcher) {
        triggerCollection()

        chatUpdatesChannel.send(ChatUpdate(
            chatId = chatId,
            reactionUpdates = listOf(
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDE00"),
                    actor = otherId,
                    action = ReactionUpdate.Action.ADDED,
                    count = 2,
                    sequence = 1,
                    reactedAt = Instant.fromEpochSeconds(1000),
                ),
                ReactionUpdate(
                    messageId = 1L,
                    emoji = Emoji("\uD83D\uDC4D"),
                    actor = otherId,
                    action = ReactionUpdate.Action.ADDED,
                    count = 5,
                    sequence = 2,
                    reactedAt = Instant.fromEpochSeconds(1000),
                ),
            ),
        ))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        val reactions = coordinator.state.value.reactionOverlays[chatId]?.get(1L)?.reactions
        assertNotNull(reactions)
        assertEquals(2, reactions.size)
        coordinator.reset()
    }

    // endregion

    // region Session lifecycle

    @Test
    fun `re-login after reset resumes chat sync on a fresh scope`() = runTest(testDispatchers.dispatcher) {
        // First session wires the delegate collectors onto the coordinator scope.
        coordinator.onUserLoggedIn(mockk(relaxed = true))
        runCurrent()

        // Logout cancels the coordinator's supervisor job (and thus its scope).
        coordinator.reset()
        runCurrent()

        // Re-login in the same process. Before the fix, the scope stayed cancelled,
        // so every launch in onUserLoggedIn was a silent no-op and incoming chat
        // updates were dropped until a process restart rebuilt the singleton.
        coordinator.onUserLoggedIn(mockk(relaxed = true))
        runCurrent()

        val msg = textMessage(id = 7, eventSequence = 1)
        chatUpdatesChannel.send(ChatUpdate(chatId = chatId, events = listOf(chatEvent(1, msg))))
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        coVerify {
            messageDataSource.upsert(chatId, match { messages ->
                messages.size == 1 && messages[0].messageId == 7L
            })
        }
        coordinator.reset()
    }

    // endregion
}
