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
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Signing out and erasing the account are separate acts, and only the second one should cost the
 * user their cached chat history.
 *
 * The Room database is per-account — [com.flipcash.app.persistence.FlipcashDatabase.init] names the
 * file from the account entropy — so a logout never has to empty a table to keep the next account's
 * data separate; the next login opens a different file. Emptying it anyway is what left a re-login
 * unable to see that the user had ever sent a tip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatCoordinatorTeardownTest {

    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var messageDataSource: ChatMessageDataSource
    private lateinit var memberDataSource: ChatMemberDataSource
    private lateinit var coordinator: RealChatCoordinator

    @Before
    fun setUp() {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns listOf<Byte>(1, 2, 3)

        val eventStreamingController = mockk<EventStreamingController>(relaxed = true)
        every { eventStreamingController.chatUpdates } returns
            Channel<ChatUpdate>(Channel.UNLIMITED).receiveAsFlow()

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(any(), any()) } returns
            Result.failure(RuntimeException("not needed"))

        metadataDataSource = mockk(relaxed = true)
        messageDataSource = mockk(relaxed = true)
        memberDataSource = mockk(relaxed = true)
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val stateHolder = ChatStateHolder()

        coordinator = RealChatCoordinator(
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
            dispatchers = TestDispatchers(TestCoroutineScheduler()),
        )
    }

    @Test
    fun `teardown leaves the persisted chat cache intact`() = runTest {
        coordinator.teardown()

        coVerify(exactly = 0) { messageDataSource.clear() }
        coVerify(exactly = 0) { metadataDataSource.clear() }
        coVerify(exactly = 0) { memberDataSource.clear() }
    }

    @Test
    fun `clearCache erases metadata, messages, and members`() = runTest {
        coordinator.clearCache()

        coVerify(exactly = 1) { metadataDataSource.clear() }
        coVerify(exactly = 1) { messageDataSource.clear() }
        coVerify(exactly = 1) { memberDataSource.clear() }
    }
}
