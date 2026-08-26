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
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
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
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The "send a tip" onboarding milestone is read off the local chat cache, so an absent TIPPED
 * message is only evidence once that cache is known to be complete. These tests hold the milestone
 * to that: it withholds an answer until the sync's catch-up has actually run, rather than reporting
 * the cold-start `false` that re-shows the tutorial to an account which has already tipped.
 *
 * The feed sync alone cannot be that signal — it reports itself `Synced` after writing the
 * conversation list, before the per-chat backfill it schedules has run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatHistoryHydrationTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    /** Stands in for the Room query behind the milestone; the backfill flips it. */
    private val tippedInCache = MutableStateFlow(false)

    private val messagingController = mockk<ChatMessagingController>(relaxed = true)
    private val chatController = mockk<ChatController>(relaxed = true)

    private fun message(messageId: Long) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(1_000 + messageId),
        unreadSeq = messageId,
        eventSequence = messageId,
    )

    private fun metadata() = ChatMetadata(
        chatId = chatId,
        type = ChatType.TIP_DM,
        members = emptyList(),
        lastMessage = message(20),
        lastActivity = Instant.fromEpochSeconds(1_000),
        latestEventSequence = 0,
    )

    private fun TestScope.buildCoordinator(): RealChatCoordinator {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId

        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns
            Channel<ChatUpdate>(Channel.UNLIMITED).receiveAsFlow()

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        every { messageDataSource.hasEverTipped() } returns tippedInCache
        // A cold cache: nothing has messages, so the sync schedules a catch-up for every chat.
        coEvery { messageDataSource.hasMessages(any()) } returns false

        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val stateHolder = ChatStateHolder()

        return RealChatCoordinator(
            feedDelegate = FeedSyncDelegate(
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
            ),
            stateHolder = stateHolder,
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    private fun feedReturns(chats: List<ChatMetadata>) {
        coEvery { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(emptyList(), null, false))
        coEvery { chatController.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.success(ChatFeedPage(chats, null, false))
    }

    private fun TestScope.collectMilestone(coordinator: RealChatCoordinator): List<Boolean?> {
        val values = mutableListOf<Boolean?>()
        coordinator.hasEverTipped().onEach { values += it }.launchIn(backgroundScope)
        runCurrent()
        return values
    }

    /**
     * Signs in and lets the session's work run to completion, then tears it down.
     *
     * The teardown is not incidental: signing in starts a heartbeat that delays forever, and
     * [runTest] drains the scheduler once the body returns — on the coordinator's own scope, which
     * is not [TestScope.backgroundScope] and so outlives the body. Left running, that loop spins
     * virtual time indefinitely and the test never ends.
     */
    private suspend fun TestScope.session(coordinator: RealChatCoordinator, body: () -> Unit) {
        coordinator.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()
        // Unconditional: a failing assertion that skipped the teardown would leave the heartbeat
        // running, and the resulting spin would bury the assertion under a test that never returns.
        try {
            body()
        } finally {
            coordinator.teardown()
        }
    }

    /**
     * The reported bug, at the read side: an account that has tipped signs in, the cache is cold,
     * and the milestone must never say `false` on the way to `true`. Anything that reports `false`
     * first draws the tutorial for a frame.
     */
    @Test
    fun `the milestone withholds an answer until the backfill has run`() = runTest {
        feedReturns(listOf(metadata()))
        // The backfill is what surfaces the tip: it is older than the chat's last message, so the
        // feed sync's own write could never have revealed it.
        coEvery { messagingController.getMessages(chatId) } coAnswers {
            tippedInCache.value = true
            Result.success(listOf(message(20)))
        }
        val coordinator = buildCoordinator()
        val values = collectMilestone(coordinator)

        assertEquals(listOf<Boolean?>(null), values, "nothing has been reconciled yet")

        session(coordinator) {
            assertEquals(
                listOf<Boolean?>(null, true),
                values,
                "the milestone must resolve straight from unknown to tipped, never through false",
            )
        }
    }

    /** Absence is real once the catch-up has run; the caller has to be released to draw. */
    @Test
    fun `the milestone reports false once the cache is reconciled`() = runTest {
        feedReturns(listOf(metadata()))
        coEvery { messagingController.getMessages(chatId) } returns Result.success(emptyList())
        val coordinator = buildCoordinator()
        val values = collectMilestone(coordinator)

        session(coordinator) {
            assertEquals(listOf<Boolean?>(null, false), values)
        }
    }

    /**
     * A warm cache is the normal case now that signing out keeps chat history, and proof of a tip
     * cannot go stale — so it answers without waiting for a round-trip.
     */
    @Test
    fun `a cached tip answers before any sync`() = runTest {
        tippedInCache.value = true
        feedReturns(emptyList())
        val coordinator = buildCoordinator()

        assertEquals(listOf<Boolean?>(true), collectMilestone(coordinator))
        coordinator.teardown()
    }

    /**
     * Offline, the answer is still not trustworthy — but waiting on a server that cannot be reached
     * would hold the wallet on its spinner indefinitely, which is worse than a stale milestone.
     */
    @Test
    fun `an unreachable server ends the wait rather than extending it`() = runTest {
        coEvery { chatController.getDmChatFeed(any(), any()) } returns
            Result.failure(RuntimeException("offline"))
        val coordinator = buildCoordinator()
        val values = collectMilestone(coordinator)

        session(coordinator) {
            assertEquals(listOf<Boolean?>(null, false), values)
        }
    }
}
