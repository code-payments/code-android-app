package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The group work reaches the rest of the app through exactly two seams: a roster change on the
 * event stream, and the login hook. Both are the coordinator's, so both are checked here rather
 * than in the delegates that do the work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupChatRoutingTest {

    // Every test tears the coordinator down. The heartbeat the login hook starts is a `while
    // (true)` on the test scheduler, so a test that leaves it running advances virtual time
    // forever instead of finishing.

    private val chatId = ChatId("11223344")
    private val selfId = listOf<Byte>(1, 2, 3)
    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private val groupFeedDelegate = mockk<GroupFeedDelegate>(relaxed = true)
    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)

    private val testDispatchers = TestDispatchers(TestCoroutineScheduler())

    private fun coordinator(): RealChatCoordinator {
        val userManager = mockk<UserManager>(relaxed = true).also {
            every { it.accountId } returns selfId
        }
        val eventStreamingController = mockk<EventStreamingController>(relaxed = true).also {
            every { it.chatUpdates } returns chatUpdatesChannel.receiveAsFlow()
            every { it.isConnected } returns true
            every { it.isStreamActive } returns true
        }
        val chatController = mockk<ChatController>(relaxed = true).also {
            coEvery { it.getDmChatFeed(any(), any()) } returns Result.failure(RuntimeException("not needed"))
        }
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
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
                messagingController = mockk<ChatMessagingController>(relaxed = true),
                metadataDataSource = metadataDataSource,
                messageDataSource = messageDataSource,
                memberDataSource = memberDataSource,
                notificationManager = mockk(relaxed = true),
                userManager = userManager,
                stateHolder = stateHolder,
                analytics = mockk(relaxed = true),
            ),
            groupFeedDelegate = groupFeedDelegate,
            stateHolder = stateHolder,
            userManager = userManager,
            networkObserver = mockk<NetworkConnectivityListener>(relaxed = true),
            featureFlags = featureFlags,
            dispatchers = testDispatchers,
        )
    }

    private val change = RosterChange.MemberJoined(
        member = ChatMember(
            userId = listOf(9, 9, 9),
            userProfile = UserProfile.Empty.copy(displayName = "Ada"),
            pointers = emptyList(),
        ),
        metadata = null,
        rosterSummary = RosterSummary(memberCount = 13, version = 5),
    )

    @Test
    fun `a roster change on the stream reaches the group delegate`() = runTest(testDispatchers.dispatcher) {
        coEvery { featureFlags.get(FeatureFlag.GroupChats) } returns true
        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        chatUpdatesChannel.send(ChatUpdate(chatId = chatId, rosterUpdates = listOf(change)))
        runCurrent()

        coVerify { groupFeedDelegate.applyRosterChanges(chatId, listOf(change)) }
        subject.teardown()
    }

    @Test
    fun `an update with no roster change does not reach the group delegate`() = runTest(testDispatchers.dispatcher) {
        coEvery { featureFlags.get(FeatureFlag.GroupChats) } returns true
        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        chatUpdatesChannel.send(ChatUpdate(chatId = chatId))
        runCurrent()

        coVerify(exactly = 0) { groupFeedDelegate.applyRosterChanges(any(), any()) }
        subject.teardown()
    }

    @Test
    fun `logging in syncs the group feed when the flag is on`() = runTest(testDispatchers.dispatcher) {
        coEvery { featureFlags.get(FeatureFlag.GroupChats) } returns true

        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        coVerify { groupFeedDelegate.syncGroupFeed() }
        subject.teardown()
    }

    @Test
    fun `logging in does not sync the group feed when the flag is off`() = runTest(testDispatchers.dispatcher) {
        coEvery { featureFlags.get(FeatureFlag.GroupChats) } returns false

        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        coVerify(exactly = 0) { groupFeedDelegate.syncGroupFeed() }
        subject.teardown()
    }

    @Test
    fun `teardown cancels the group delegate's jobs`() = runTest(testDispatchers.dispatcher) {
        coEvery { featureFlags.get(FeatureFlag.GroupChats) } returns true
        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        subject.teardown()

        coVerify { groupFeedDelegate.cancelJobs() }
    }
}
