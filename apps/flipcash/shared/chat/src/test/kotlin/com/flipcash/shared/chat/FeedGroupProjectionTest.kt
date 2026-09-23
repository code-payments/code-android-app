package com.flipcash.shared.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A group is not a DM with more people in it. It has no counterparty to resolve a title from, and
 * its roster arrives a page at a time, so the signed-in user's own member row — the one the unread
 * count is read off — may not be among the members the device holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedGroupProjectionTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    private val dmHex = "aabbccdd"
    private val groupHex = "11223344"

    private fun entity(chatIdHex: String, type: ChatType, isMember: Boolean = true) =
        ChatMetadataEntity(
            chatIdHex = chatIdHex,
            chatType = type.name,
            lastActivityEpochMs = 2_000,
            lastMessageId = 2,
            isMember = isMember,
        )

    private fun message(messageId: Long) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
    )

    private fun selfMember(readPointer: Long) = ChatMember(
        userId = selfId,
        userProfile = UserProfile.Empty,
        pointers = listOf(
            MessagePointer(
                type = PointerType.READ,
                userId = selfId,
                value = readPointer,
                timestamp = Instant.fromEpochSeconds(1_000),
            )
        ),
    )

    private val addressableOther = ChatMember(
        userId = otherId,
        userProfile = UserProfile.Empty.copy(
            displayName = "Ada",
            phoneNumber = VerifiableContactMethod("+15551234567", verified = true),
        ),
        pointers = emptyList(),
    )

    /** Wires a [FeedSyncDelegate] over a fixed set of rows and rosters. */
    private inner class Harness(
        entities: List<ChatMetadataEntity>,
        members: Map<String, List<ChatMember>>,
    ) {
        private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
            coEvery { it.getLatestVisibleByChat() } returns entities.associate { e -> e.chatIdHex to message(2) }
        }

        private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
            every { source.observeAll() } returns flowOf(entities)
            every { source.toMetadata(any(), any(), any()) } answers {
                val row = firstArg<ChatMetadataEntity>()
                ChatMetadata(
                    chatId = ChatId(row.chatIdHex),
                    type = ChatType.valueOf(row.chatType),
                    members = secondArg(),
                    lastMessage = thirdArg(),
                    lastActivity = Instant.fromEpochMilliseconds(row.lastActivityEpochMs),
                    title = row.title,
                    rosterSummary = RosterSummary(memberCount = row.memberCount, version = row.rosterVersion),
                )
            }
        }

        private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also { source ->
            every { source.observeAll() } returns flowOf(members)
        }

        // Marked synced so an empty projection still emits: these tests are about what the feed
        // holds, not about when it is first known.
        private val stateHolder = ChatStateHolder().apply {
            update { it.copy(feedSyncState = FeedSyncState.Synced) }
        }

        val delegate = FeedSyncDelegate(
            chatController = mockk<ChatController>(relaxed = true),
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            stateHolder = stateHolder,
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )

        suspend fun feed(scope: TestScope, vararg types: ChatType): List<ChatSummary> {
            delegate.initialize(scope.backgroundScope)
            delegate.observeFeedFromDb()
            scope.runCurrent()
            return delegate.feed(*types).first()
        }
    }

    @Test
    fun `a group with no counterparty still reaches the feed`() = runTest {
        val harness = Harness(
            entities = listOf(entity(groupHex, ChatType.GROUP)),
            members = mapOf(groupHex to listOf(selfMember(readPointer = 2))),
        )

        val feed = harness.feed(this, ChatType.GROUP)

        assertEquals(1, feed.size)
        assertEquals(ChatId(groupHex), feed.single().metadata.chatId)
    }

    /**
     * The roster is paged, so the members the device holds are a slice of a larger list. Treating
     * a missing self row as a read pointer of zero would put an unread splat on every group until
     * the slice happened to include the signed-in user.
     */
    @Test
    fun `a group whose roster does not include you reports no unread`() = runTest {
        val harness = Harness(
            entities = listOf(entity(groupHex, ChatType.GROUP)),
            members = mapOf(groupHex to listOf(addressableOther)),
        )

        val feed = harness.feed(this, ChatType.GROUP)

        assertEquals(0, feed.single().unreadCount)
    }

    @Test
    fun `a group counts unread against your own read pointer`() = runTest {
        val harness = Harness(
            entities = listOf(entity(groupHex, ChatType.GROUP)),
            members = mapOf(groupHex to listOf(selfMember(readPointer = 1), addressableOther)),
        )

        val feed = harness.feed(this, ChatType.GROUP)

        assertEquals(1, feed.single().unreadCount)
    }

    /** DM behaviour is unchanged: an absent self row still reads as a pointer of zero. */
    @Test
    fun `a DM whose roster does not include you still counts unread`() = runTest {
        val harness = Harness(
            entities = listOf(entity(dmHex, ChatType.CONTACT_DM)),
            members = mapOf(dmHex to listOf(addressableOther)),
        )

        val feed = harness.feed(this, ChatType.CONTACT_DM)

        assertEquals(1, feed.single().unreadCount)
    }

    @Test
    fun `a group you have left is not in the feed`() = runTest {
        val harness = Harness(
            entities = listOf(entity(groupHex, ChatType.GROUP, isMember = false)),
            members = mapOf(groupHex to listOf(selfMember(readPointer = 2))),
        )

        val feed = harness.feed(this, ChatType.GROUP)

        assertEquals(emptyList(), feed)
    }

    @Test
    fun `asking for several types returns all of them`() = runTest {
        val harness = Harness(
            entities = listOf(entity(dmHex, ChatType.CONTACT_DM), entity(groupHex, ChatType.GROUP)),
            members = mapOf(
                dmHex to listOf(selfMember(readPointer = 2), addressableOther),
                groupHex to listOf(selfMember(readPointer = 2)),
            ),
        )

        val feed = harness.feed(this, ChatType.CONTACT_DM, ChatType.GROUP)

        assertEquals(
            listOf(ChatId(dmHex), ChatId(groupHex)),
            feed.map { it.metadata.chatId },
        )
    }

    @Test
    fun `asking for one type leaves the others out`() = runTest {
        val harness = Harness(
            entities = listOf(entity(dmHex, ChatType.CONTACT_DM), entity(groupHex, ChatType.GROUP)),
            members = mapOf(
                dmHex to listOf(selfMember(readPointer = 2), addressableOther),
                groupHex to listOf(selfMember(readPointer = 2)),
            ),
        )

        val feed = harness.feed(this, ChatType.CONTACT_DM)

        assertEquals(listOf(ChatId(dmHex)), feed.map { it.metadata.chatId })
    }
}
