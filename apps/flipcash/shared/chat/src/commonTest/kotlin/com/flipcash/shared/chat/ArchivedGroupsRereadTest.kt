package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatFolder
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.GroupFeedDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.flipcash.shared.chat.testing.TestDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The archived-group re-read that follows a follow ack, or the no-ack wait running out, reads to
 * the last page. An archived group on a later page that moved while unfollowed gets its catch-up
 * the same as one on the first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedGroupsRereadTest {

    private val first = ChatId("11111111")
    private val second = ChatId("22222222")
    private val secondPage: PagingToken = listOf(7, 7)

    private val streamEvents = Channel<EventStreamDelegate.Event>(Channel.UNLIMITED)
    private val controller = mockk<ChatController>(relaxed = true)
    private val feedDelegate = mockk<FeedSyncDelegate>(relaxed = true)
    private val messagingDelegate = mockk<MessagingDelegate>(relaxed = true)
    private val testDispatchers = TestDispatchers(TestCoroutineScheduler())

    private fun group(chatId: ChatId) = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        title = "Archived",
        rosterSummary = RosterSummary(memberCount = 2, version = 1),
        latestEventSequence = 4,
    )

    @BeforeTest
    fun setUp() {
        coEvery { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) } answers {
            val page = if (firstArg<QueryOptions>().token == secondPage) {
                ChatFeedPage(chats = listOf(group(second)), pagingToken = null, hasMore = false)
            } else {
                ChatFeedPage(chats = listOf(group(first)), pagingToken = secondPage, hasMore = true)
            }
            Result.success(page)
        }
        every { feedDelegate.events } returns emptyFlow()

        val eventStreamDelegate = mockk<EventStreamDelegate>(relaxed = true)
        every { eventStreamDelegate.events } returns streamEvents.receiveAsFlow()

        val userManager = mockk<UserManager>(relaxed = true)
        val groupFeedDelegate = GroupFeedDelegate(
            chatController = controller,
            // No cursor held for either group, so each catch-up asks for the transcript.
            metadataDataSource = mockk(relaxed = true),
            memberDataSource = mockk(relaxed = true),
            messageDataSource = mockk(relaxed = true),
            rosterStateHolder = mockk(relaxed = true),
            userManager = userManager,
        )

        // Built for its event routing, which starts at construction.
        RealChatCoordinator(
            feedDelegate = feedDelegate,
            eventStreamDelegate = eventStreamDelegate,
            dmChatResolverDelegate = mockk(relaxed = true),
            messagingDelegate = messagingDelegate,
            groupFeedDelegate = groupFeedDelegate,
            reactionsDelegate = mockk(relaxed = true),
            stateHolder = ChatStateHolder(),
            draftStore = mockk(relaxed = true),
            userManager = userManager,
            networkObserver = mockk(relaxed = true),
            dispatchers = testDispatchers,
        )
    }

    @Test
    fun `the re-read after a follow reads every page of archived groups`() =
        runTest(testDispatchers.dispatcher) {
            streamEvents.send(EventStreamDelegate.Event.ArchivedGroupsStale)
            advanceUntilIdle()

            coVerify { controller.getGroupChatFeed(match { it.token == secondPage }, ChatFolder.ARCHIVED) }
            coVerify { feedDelegate.writeFeed(listOf(group(first), group(second))) }
            coVerify { messagingDelegate.loadMessages(first) }
            coVerify { messagingDelegate.loadMessages(second) }
        }
}
