package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.RosterStateHolder
import com.flipcash.shared.chat.internal.delegates.GroupFeedDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Group membership is a state the device has to hold locally, because the two things that change
 * it — your own join and your own removal — reach the client as a roster change on a stream that
 * can be down, and as a call whose result the list should not wait for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupFeedDelegateTest {

    private val chatId = ChatId("11223344")
    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    private val controller = mockk<ChatController>(relaxed = true)
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
    private val rosterStateHolder = mockk<RosterStateHolder>(relaxed = true)

    private val subject = GroupFeedDelegate(
        chatController = controller,
        metadataDataSource = metadataDataSource,
        memberDataSource = memberDataSource,
        messageDataSource = messageDataSource,
        rosterStateHolder = rosterStateHolder,
        userManager = mockk<UserManager>(relaxed = true).also {
            every { it.accountId } returns selfId
        },
    )

    private fun member(userId: List<Byte>) = ChatMember(
        userId = userId,
        userProfile = UserProfile.Empty.copy(displayName = "Ada"),
        pointers = emptyList(),
    )

    private fun group(version: Long = 4) = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = listOf(member(selfId)),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        title = "Flipcash Staff",
        rosterSummary = RosterSummary(memberCount = 12, version = version),
    )

    @Test
    fun `a sync persists the groups it fetched`() = runTest {
        coEvery { controller.getGroupChatFeed(any()) } returns Result.success(
            ChatFeedPage(chats = listOf(group()), pagingToken = null, hasMore = false)
        )

        subject.performGroupFeedSync()

        coVerify { metadataDataSource.upsert(listOf(group())) }
        coVerify { memberDataSource.upsert(chatId, group().members) }
    }

    /**
     * A page is not the whole feed, so a group absent from it has not been left. Reconciliation
     * belongs to the paged mediator, which knows when a pass finished.
     */
    @Test
    fun `a sync does not clear the membership of a group it did not see`() = runTest {
        coEvery { controller.getGroupChatFeed(any()) } returns Result.success(
            ChatFeedPage(chats = emptyList(), pagingToken = null, hasMore = true)
        )

        subject.performGroupFeedSync()

        coVerify(exactly = 0) { metadataDataSource.setMembership(any<ChatId>(), any()) }
    }

    @Test
    fun `a failed sync writes nothing`() = runTest {
        coEvery { controller.getGroupChatFeed(any()) } returns Result.failure(Throwable("unreachable"))

        subject.performGroupFeedSync()

        coVerify(exactly = 0) { metadataDataSource.upsert(any<List<ChatMetadata>>()) }
    }

    @Test
    fun `joining persists the chat and asks for its messages`() = runTest {
        coEvery { controller.joinChat(chatId) } returns Result.success(group())

        val result = subject.join(chatId)

        assertTrue(result.isSuccess)
        coVerify { metadataDataSource.upsert(listOf(group())) }
        coVerify { memberDataSource.upsert(chatId, group().members) }
        coVerify { metadataDataSource.setMembership(chatId, isMember = true) }
        assertEquals(GroupFeedDelegate.Event.LoadMessages(chatId), subject.events.first())
    }

    @Test
    fun `a failed join writes nothing`() = runTest {
        coEvery { controller.joinChat(chatId) } returns Result.failure(Throwable("rules not met"))

        val result = subject.join(chatId)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { metadataDataSource.setMembership(any<ChatId>(), any()) }
    }

    /**
     * The row is cleared before the call, so the chat leaves the list on the tap rather than a
     * round trip later.
     */
    @Test
    fun `leaving clears the membership before the call returns`() = runTest {
        coEvery { controller.leaveChat(chatId) } returns Result.success(Unit)

        subject.leave(chatId)

        coVerify(exactly = 1) { metadataDataSource.setMembership(chatId, isMember = false) }
        coVerify(exactly = 0) { metadataDataSource.setMembership(chatId, isMember = true) }
    }

    @Test
    fun `a failed leave puts the chat back`() = runTest {
        coEvery { controller.leaveChat(chatId) } returns Result.failure(Throwable("unreachable"))

        val result = subject.leave(chatId)

        assertTrue(result.isFailure)
        coVerify { metadataDataSource.setMembership(chatId, isMember = false) }
        coVerify { metadataDataSource.setMembership(chatId, isMember = true) }
    }

    /** `metadata` is set only on your own join, and is the whole chat to put in the list. */
    @Test
    fun `your own join adds the chat to the list`() = runTest {
        val change = RosterChange.MemberJoined(
            member = member(selfId),
            metadata = group(version = 5),
            rosterSummary = RosterSummary(memberCount = 12, version = 5),
        )

        subject.applyRosterChanges(chatId, listOf(change))

        coVerify { metadataDataSource.upsert(listOf(group(version = 5))) }
        coVerify { metadataDataSource.setMembership(chatId, isMember = true) }
        assertEquals(GroupFeedDelegate.Event.LoadMessages(chatId), subject.events.first())
    }

    @Test
    fun `someone else joining is left to the roster holder`() = runTest {
        val change = RosterChange.MemberJoined(
            member = member(otherId),
            metadata = null,
            rosterSummary = RosterSummary(memberCount = 13, version = 5),
        )

        subject.applyRosterChanges(chatId, listOf(change))

        coVerify(exactly = 0) { metadataDataSource.setMembership(any<ChatId>(), any()) }
        coVerify { rosterStateHolder.apply(chatId, listOf(change)) }
    }

    @Test
    fun `being removed drops the chat out of the list`() = runTest {
        val change = RosterChange.MemberLeft(
            userId = selfId,
            rosterSummary = RosterSummary(memberCount = 11, version = 5),
        )

        subject.applyRosterChanges(chatId, listOf(change))

        coVerify { metadataDataSource.setMembership(chatId, isMember = false) }
    }

    @Test
    fun `someone else being removed leaves your membership alone`() = runTest {
        val change = RosterChange.MemberLeft(
            userId = otherId,
            rosterSummary = RosterSummary(memberCount = 11, version = 5),
        )

        subject.applyRosterChanges(chatId, listOf(change))

        coVerify(exactly = 0) { metadataDataSource.setMembership(any<ChatId>(), any()) }
        coVerify { rosterStateHolder.apply(chatId, listOf(change)) }
    }
}
