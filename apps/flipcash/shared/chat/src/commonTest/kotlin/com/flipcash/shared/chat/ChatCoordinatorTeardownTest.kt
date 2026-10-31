package com.flipcash.shared.chat

import com.flipcash.shared.chat.testing.TestDispatchers
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
import com.flipcash.shared.chat.internal.delegates.GroupFeedDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
class ChatCoordinatorTeardownTest {

    private lateinit var metadataDataSource: ChatMetadataDataSource
    private lateinit var messageDataSource: ChatMessageDataSource
    private lateinit var memberDataSource: ChatMemberDataSource
    private lateinit var draftStore: ChatDraftStore
    private lateinit var archiveStore: ChatArchiveStore
    private lateinit var groupFeedDelegate: GroupFeedDelegate
    private lateinit var hostInvites: HostInvites
    private lateinit var groupKeyEnvelopes: com.flipcash.services.chat.GroupKeyEnvelopeStore
    private lateinit var coordinator: RealChatCoordinator

    @BeforeTest
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
        draftStore = mockk(relaxed = true)
        archiveStore = mockk(relaxed = true)
        groupFeedDelegate = mockk(relaxed = true)
        hostInvites = mockk(relaxed = true)
        groupKeyEnvelopes = mockk(relaxed = true)
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
                notifications = mockk(relaxed = true),
                userManager = userManager,
                stateHolder = stateHolder,
                analytics = mockk(relaxed = true),
                senderResolver = mockk(relaxed = true),
            ),
            stateHolder = stateHolder,
            draftStore = draftStore,
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = TestDispatchers(TestCoroutineScheduler()),
            groupFeedDelegate = groupFeedDelegate,
            reactionsDelegate = mockk(relaxed = true),
            archiveStore = archiveStore,
            hostInvites = hostInvites,
            groupKeyEnvelopes = groupKeyEnvelopes,
        )
    }

    @Test
    fun `teardown leaves the persisted chat cache intact`() = runTest {
        coordinator.teardown()

        coVerify(exactly = 0) { messageDataSource.clear() }
        coVerify(exactly = 0) { metadataDataSource.clear() }
        coVerify(exactly = 0) { memberDataSource.clear() }
    }

    /**
     * A draft belongs to the cache, not to the session. It survives a logout for the same reason
     * the transcript does, and the half-written message is the part the user would most notice
     * losing to a sign-out they expected to be reversible.
     */
    @Test
    fun `teardown leaves drafts alone`() = runTest {
        coordinator.teardown()

        coVerify(exactly = 0) { draftStore.clearAll() }
    }

    /** The invites belong to the account, and the links among them are as good as the chat key. */
    @Test
    fun `teardown drops the held invites`() = runTest {
        coordinator.teardown()

        verify(exactly = 1) { hostInvites.clear() }
    }

    @Test
    fun `clearCache erases metadata, messages, and members`() = runTest {
        coordinator.clearCache()

        coVerify(exactly = 1) { metadataDataSource.clear() }
        coVerify(exactly = 1) { messageDataSource.clear() }
        coVerify(exactly = 1) { memberDataSource.clear() }
    }

    /** Account deletion is the one path that erases a draft, because nothing else erases anything. */
    @Test
    fun `clearCache erases drafts`() = runTest {
        coordinator.clearCache()

        coVerify(exactly = 1) { draftStore.clearAll() }
    }

    /** Like drafts: logout keeps the set (the database is per-account), erasure wipes it. */
    @Test
    fun `clearCache drops the archive set, teardown keeps it`() = runTest {
        coordinator.teardown()
        coVerify(exactly = 0) { archiveStore.clearAll() }

        coordinator.clearCache()
        coVerify(exactly = 1) { archiveStore.clearAll() }
    }

    /** Wrapped chat keys live in the per-account database, so erasure wipes them and teardown keeps them. */
    @Test
    fun `clearCache drops stored key envelopes, teardown keeps them`() = runTest {
        coordinator.teardown()
        coVerify(exactly = 0) { groupKeyEnvelopes.clear() }

        coordinator.clearCache()
        coVerify(exactly = 1) { groupKeyEnvelopes.clear() }
    }

    @Test
    fun `a successful leave unarchives the chat`() = runTest {
        val chatId = ChatId("aabbccdd")
        coEvery { groupFeedDelegate.leave(chatId) } returns Result.success(Unit)

        coordinator.leave(chatId)

        coVerify(exactly = 1) { archiveStore.unarchive(chatId) }
    }

    @Test
    fun `a failed leave keeps the archive record`() = runTest {
        val chatId = ChatId("aabbccdd")
        coEvery { groupFeedDelegate.leave(chatId) } returns Result.failure(RuntimeException("nope"))

        coordinator.leave(chatId)

        coVerify(exactly = 0) { archiveStore.unarchive(any()) }
    }
}
