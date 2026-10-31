package com.flipcash.shared.chat

import androidx.lifecycle.LifecycleOwner
import com.flipcash.app.core.dispatchers.TestDispatchers
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
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterPage
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The group work reaches the rest of the app through the coordinator and nowhere else: a roster
 * change on the event stream, and every trigger that re-syncs the conversation list. All of them
 * are checked here rather than in the delegates that do the work.
 *
 * The refresh triggers are the reason this file is not only about routing. A group's row comes
 * from `GroupFeedDelegate` alone, so a trigger that reaches `FeedSyncDelegate` by itself leaves
 * the group half of the list on whatever the login pass fetched — which is what these assert
 * against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupChatRoutingTest {

    private val chatId = ChatId("11223344")
    private val selfId = listOf<Byte>(1, 2, 3)
    private val chatUpdatesChannel = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)

    private val groupFeedDelegate = mockk<GroupFeedDelegate>(relaxed = true)
    private val chatController = mockk<ChatController>(relaxed = true).also {
        coEvery { it.getDmChatFeed(any(), any()) } returns Result.failure(RuntimeException("not needed"))
    }

    private val testDispatchers = TestDispatchers(TestCoroutineScheduler())

    private fun coordinator(rosterUpdates: RosterUpdateSink = RosterUpdateSink.None): RealChatCoordinator {
        val userManager = mockk<UserManager>(relaxed = true).also {
            every { it.accountId } returns selfId
        }
        val eventStreamingController = mockk<EventStreamingController>(relaxed = true).also {
            every { it.chatUpdates } returns chatUpdatesChannel.receiveAsFlow()
            every { it.isConnected } returns true
            every { it.isStreamActive } returns true
        }
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
        val stateHolder = ChatStateHolder()

        return RealChatCoordinator(
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
                notifications = mockk(relaxed = true),
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
            rosterUpdates = rosterUpdates,
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

    /**
     * Runs [block] against a logged-in coordinator and tears it down afterwards, however it ends.
     *
     * The `finally` is the point. The heartbeat the login hook starts is a `while (true)` on the
     * test scheduler, so a test that fails its assertion before reaching teardown hangs the run
     * advancing virtual time instead of reporting the failure.
     */
    private suspend fun TestScope.loggedIn(
        rosterUpdates: RosterUpdateSink = RosterUpdateSink.None,
        block: suspend (RealChatCoordinator) -> Unit,
    ) {
        val subject = coordinator(rosterUpdates)
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()
        try {
            block(subject)
        } finally {
            subject.teardown()
        }
    }

    @Test
    fun `a roster change on the stream reaches the group delegate`() = runTest(testDispatchers.dispatcher) {
        loggedIn {
            chatUpdatesChannel.send(ChatUpdate(chatId = chatId, rosterUpdates = listOf(change)))
            runCurrent()

            coVerify { groupFeedDelegate.applyRosterChanges(chatId, listOf(change)) }
        }
    }

    @Test
    fun `a removal on the stream drops the member from the held roster as the count moves`() = runTest(testDispatchers.dispatcher) {
        val ada = ChatMember(
            userId = listOf(9, 9, 9),
            userProfile = UserProfile.Empty.copy(displayName = "Ada"),
            pointers = emptyList(),
        )
        val rosters = GroupRosters(chatController)
        coEvery { chatController.getRoster(chatId, any()) } returns
            Result.success(RosterPage(listOf(ada), RosterSummary(2, 4), null, hasMore = false))
        rosters.load(chatId)
        assertEquals(1, rosters.state.value[chatId]?.size)
        // After the ban the server's roster holds nobody else.
        coEvery { chatController.getRoster(chatId, any()) } returns
            Result.success(RosterPage(emptyList(), RosterSummary(1, 5), null, hasMore = false))

        loggedIn(rosters) {
            // What the members who stay are sent for a removal: the new count, naming no one.
            chatUpdatesChannel.send(
                ChatUpdate(chatId = chatId, rosterUpdates = listOf(RosterChange.MembershipChanged(RosterSummary(1, 5)))),
            )
            runCurrent()

            coVerify { groupFeedDelegate.applyRosterChanges(chatId, any()) }
            assertEquals(emptyList<ChatMember>(), rosters.state.value[chatId])
        }
    }

    @Test
    fun `an update with no roster change does not reach the group delegate`() = runTest(testDispatchers.dispatcher) {
        loggedIn {
            chatUpdatesChannel.send(ChatUpdate(chatId = chatId))
            runCurrent()

            coVerify(exactly = 0) { groupFeedDelegate.applyRosterChanges(any(), any()) }
        }
    }

    @Test
    fun `logging in syncs the group feed`() = runTest(testDispatchers.dispatcher) {
        loggedIn {
            coVerify(exactly = 1) { groupFeedDelegate.fetchGroupFeed() }
        }
    }

    @Test
    fun `refreshing the feed syncs the group feed`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            clearMocks(groupFeedDelegate, answers = false)

            // What a chat push and both payment delegates call.
            subject.refreshFeed()
            runCurrent()

            coVerify(exactly = 1) { groupFeedDelegate.fetchGroupFeed() }
        }
    }

    @Test
    fun `refreshing the feed still syncs the DM feeds`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            clearMocks(chatController, answers = false)

            // [RealChatCoordinator.refreshFeed] overrides the FeedOperations delegation, so the
            // half that used to be the only one running needs asserting as well as the half that
            // did not.
            subject.refreshFeed()
            runCurrent()

            coVerify(exactly = 1) { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) }
            coVerify(exactly = 1) { chatController.getDmChatFeed(ChatType.TIP_DM, any()) }
        }
    }

    @Test
    fun `resuming from the background syncs the group feed`() = runTest(testDispatchers.dispatcher) {
        loggedIn { subject ->
            clearMocks(groupFeedDelegate, answers = false)

            subject.onStart(mockk<LifecycleOwner>(relaxed = true))
            runCurrent()

            coVerify(exactly = 1) { groupFeedDelegate.fetchGroupFeed() }
        }
    }

    @Test
    fun `teardown cancels the group delegate's jobs`() = runTest(testDispatchers.dispatcher) {
        val subject = coordinator()
        subject.onUserLoggedIn(mockk<AccountCluster>(relaxed = true))
        runCurrent()

        subject.teardown()

        coVerify { groupFeedDelegate.cancelJobs() }
    }
}
