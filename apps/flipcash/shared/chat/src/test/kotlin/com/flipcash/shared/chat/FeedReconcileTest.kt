package com.flipcash.shared.chat

import androidx.lifecycle.LifecycleOwner
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatFeedWriter
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterSummary
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
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * Launch fetches the DM and group feeds once and writes them once. A foreground that lands while
 * that fetch is in flight shares it rather than cancelling and restarting it, which is what used to
 * double the launch traffic and let the list change twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedReconcileTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val dmChat = chat("aa", ChatType.CONTACT_DM)
    private val groupChat = chat("bb", ChatType.GROUP)

    private fun chat(hex: String, type: ChatType) = ChatMetadata(
        chatId = ChatId(hex),
        type = type,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1000),
    )

    private val groupGate = CompletableDeferred<Unit>()
    private val chatController = mockk<ChatController>(relaxed = true).also {
        coEvery { it.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(listOf(dmChat), null, false))
        coEvery { it.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.success(ChatFeedPage(emptyList(), null, false))
    }
    private val groupFeedDelegate = mockk<GroupFeedDelegate>(relaxed = true).also {
        coEvery { it.fetchGroupFeed() } coAnswers {
            groupGate.await()
            listOf(groupChat)
        }
    }
    private val feedWriter = mockk<ChatFeedWriter>(relaxed = true)
    private val testDispatchers = TestDispatchers(TestCoroutineScheduler())

    private fun coordinator(): RealChatCoordinator {
        val userManager = mockk<UserManager>(relaxed = true).also {
            every { it.accountId } returns selfId
        }
        val eventStreamingController = mockk<EventStreamingController>(relaxed = true).also {
            every { it.chatUpdates } returns Channel<ChatUpdate>().receiveAsFlow()
            every { it.isConnected } returns true
            every { it.isStreamActive } returns true
        }
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val stateHolder = ChatStateHolder()
        val messagingController = mockk<ChatMessagingController>(relaxed = true).also {
            coEvery { it.getMessages(any(), any()) } returns Result.success(emptyList())
        }

        return RealChatCoordinator(
            feedDelegate = FeedSyncDelegate(
                messagingController = mockk(relaxed = true),
                chatController = chatController,
                metadataDataSource = metadataDataSource,
                messageDataSource = messageDataSource,
                memberDataSource = memberDataSource,
                stateHolder = stateHolder,
                userManager = userManager,
                feedWriter = feedWriter,
            ),
            eventStreamDelegate = EventStreamDelegate(
                eventStreamingController = eventStreamingController,
                messagingController = mockk<ChatMessagingController>(relaxed = true),
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
            groupFeedDelegate = groupFeedDelegate,
            reactionsDelegate = mockk(relaxed = true),
            stateHolder = stateHolder,
            draftStore = mockk<ChatDraftStore>(relaxed = true),
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            dispatchers = testDispatchers,
        )
    }

    private suspend fun TestScope.loggedIn(block: suspend (RealChatCoordinator) -> Unit) {
        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()
        try {
            block(subject)
        } finally {
            groupGate.complete(Unit)
            subject.teardown()
        }
    }

    @Test
    fun `the DM and group feeds land in one write`() = runTest(testDispatchers.dispatcher) {
        loggedIn {
            groupGate.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { feedWriter.write(match { it.toSet() == setOf(dmChat, groupChat) }) }
        }
    }

    @Test
    fun `a foreground during the launch sync shares it instead of restarting it`() =
        runTest(testDispatchers.dispatcher) {
            loggedIn { subject ->
                // The group fetch is still in flight: the launch sync has not finished.
                subject.onStart(mockk<LifecycleOwner>(relaxed = true))
                runCurrent()
                groupGate.complete(Unit)
                runCurrent()

                coVerify(exactly = 1) { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
                coVerify(exactly = 1) { groupFeedDelegate.fetchGroupFeed() }
                coVerify(exactly = 1) { feedWriter.write(any()) }
            }
        }

    @Test
    fun `a push during an in-flight sync queues exactly one trailing fetch`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            subject.refreshFeed()
            runCurrent()
            groupGate.complete(Unit)
            runCurrent()

            coVerify(exactly = 2) { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        }
    }

    @Test
    fun `several pushes during one sync still produce one trailing fetch`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            repeat(5) { subject.refreshFeed() }
            runCurrent()
            groupGate.complete(Unit)
            runCurrent()

            coVerify(exactly = 2) { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        }
    }

    @Test
    fun `a refresh after the sync has finished fetches again`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            groupGate.complete(Unit)
            runCurrent()

            subject.refreshFeed()
            runCurrent()

            coVerify(exactly = 2) { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        }
    }
}
