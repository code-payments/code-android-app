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
import com.flipcash.services.models.chat.ChatId
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who owns the active chat when one chat screen replaces another.
 *
 * The active chat suppresses notifications for the conversation on screen
 * ([com.flipcash.app.notifications.NotificationService] drops a push for it). Each chat screen
 * claims it on open and released it on teardown — unconditionally, which is the bug: opening a
 * chat *directly from another chat* (a group mention in the feed, a tap through from a DM) keeps
 * both entries alive for a moment, and Navigation 3 disposes the outgoing one after the incoming
 * one has already claimed the chat.
 *
 * An on-device trace caught the window: `Navigating to Chat[VDbei…]` at 13:58:55.368 and the
 * outgoing `[W4rX…]` screen's dispose at 13:58:55.892 — 524ms later, clearing the chat the user
 * was by then looking at. Every push for it notified from that point on. Going back to the chat
 * list first put the dispose ahead of the open and hid the bug, which is why it looked like it
 * only happened to some chats.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActiveChatHandoffTest {

    private val leftChat = ChatId("aabbccdd")
    private val openedChat = ChatId("11223344")

    private lateinit var coordinator: RealChatCoordinator

    @Before
    fun setUp() {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns listOf<Byte>(1, 2, 3)

        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns
            Channel<ChatUpdate>(Channel.UNLIMITED).receiveAsFlow()
        every { eventStreamingController.streamFailures } returns emptyFlow()

        val chatController = mockk<ChatController>(relaxed = true)
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val stateHolder = ChatStateHolder()

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
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = TestDispatchers(TestCoroutineScheduler()),
            groupFeedDelegate = mockk(relaxed = true),
            reactionsDelegate = mockk(relaxed = true),
        )
    }

    // Nothing here logs in or foregrounds the coordinator, so no heartbeat runs and these stay
    // plain synchronous calls: the active chat is set and read on the caller's thread.

    @Test
    fun `a chat opened from another chat survives the outgoing screen's teardown`() {
        coordinator.setActiveChatId(leftChat)

        // The incoming screen claims the chat while the outgoing entry is still alive.
        coordinator.setActiveChatId(openedChat)
        coordinator.clearActiveChat(leftChat)

        assertTrue(coordinator.isActiveChat(openedChat))
    }

    @Test
    fun `leaving a chat for the list releases it`() {
        coordinator.setActiveChatId(leftChat)

        coordinator.clearActiveChat(leftChat)

        assertFalse(coordinator.isActiveChat(leftChat))
    }

    @Test
    fun `a chat that never opened releases nothing`() {
        coordinator.setActiveChatId(openedChat)

        // A chat screen torn down before it ever resolved a chat id has no claim to release.
        coordinator.clearActiveChat(null)

        assertTrue(coordinator.isActiveChat(openedChat))
    }

    @Test
    fun `a chat torn down in the background does not come back on foreground`() {
        val owner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        coordinator.setActiveChatId(openedChat)

        // onStop stashes the active chat so foregrounding restores it. A screen destroyed while
        // the app is away has to clear the stash too, or it returns as the active chat with
        // nothing on screen.
        coordinator.onStop(owner)
        coordinator.clearActiveChat(openedChat)
        coordinator.onStart(owner)

        assertFalse(coordinator.isActiveChat(openedChat))
    }
}
