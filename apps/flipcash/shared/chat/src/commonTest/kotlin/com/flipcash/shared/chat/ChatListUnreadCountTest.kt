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
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.getcode.solana.keys.PublicKey
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
 * The Chats tab badge counts the chats the Chats list shows: tip DMs and groups. It used to count
 * tip DMs only, so a list whose only unread rows were groups sat under a tab with no badge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListUnreadCountTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)

    private val tipHex = "aabbccdd"
    private val contactHex = "55667788"
    private val groupHex = "11223344"

    private fun entity(chatIdHex: String, type: ChatType) = ChatMetadataEntity(
        chatIdHex = chatIdHex,
        chatType = type.name,
        lastActivityEpochMs = 2_000,
        lastMessageId = 2,
        isMember = true,
    )

    private fun message(messageId: Long) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
    )

    /** Read up to message [readThrough] of 2; 1 leaves each chat below one unread message. */
    private fun selfReadThrough(readThrough: Long) = ChatMember(
        userId = selfId,
        userProfile = UserProfile.Empty,
        pointers = listOf(
            MessagePointer(
                type = PointerType.READ,
                userId = selfId,
                value = readThrough,
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

    private suspend fun TestScope.unreadChatListCount(vararg unread: Pair<String, ChatType>): Int =
        feedDelegate(unread.toList()).observeUnreadChatListCount().first()

    /**
     * Builds the feed over [chats]. [readThrough] is the viewer's read pointer (2 reads everything);
     * [hostedGroups] are private groups the viewer created.
     */
    private suspend fun TestScope.feedDelegate(
        chats: List<Pair<String, ChatType>>,
        readThrough: Long = 1,
        hostedGroups: Set<String> = emptySet(),
    ): FeedSyncDelegate {
        val unread = chats.toTypedArray()
        val entities = unread.map { (hex, type) -> entity(hex, type) }
        val members = unread.associate { (hex, type) ->
            val self = selfReadThrough(readThrough)
            hex to if (type == ChatType.GROUP) listOf(self) else listOf(self, addressableOther)
        }

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
            coEvery { it.getLatestVisibleByChat() } returns entities.associate { e -> e.chatIdHex to message(2) }
            coEvery { it.getUnreadSeq(any(), any()) } answers { secondArg() }
        }
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
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
                    creator = if (row.chatIdHex in hostedGroups) selfId else null,
                    isPrivate = row.chatIdHex in hostedGroups,
                )
            }
        }
        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also { source ->
            every { source.observeAll() } returns flowOf(members)
        }

        val delegate = FeedSyncDelegate(
            messagingController = mockk(relaxed = true),
            chatController = mockk<ChatController>(relaxed = true),
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            stateHolder = ChatStateHolder().apply { update { it.copy(feedSyncState = FeedSyncState.Synced) } },
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )
        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        return delegate
    }

    @Test
    fun `an unread group counts toward the badge`() = runTest {
        assertEquals(1, unreadChatListCount(groupHex to ChatType.GROUP))
    }

    @Test
    fun `an unread contact DM does not count toward the badge`() = runTest {
        assertEquals(0, unreadChatListCount(contactHex to ChatType.CONTACT_DM))
    }

    @Test
    fun `the badge counts unread tip DMs and groups but not contact DMs`() = runTest {
        val count = unreadChatListCount(
            tipHex to ChatType.TIP_DM,
            groupHex to ChatType.GROUP,
            contactHex to ChatType.CONTACT_DM,
        )

        assertEquals(2, count)
    }

    private fun waiting(chatIdHex: String) = mapOf(
        ChatId(chatIdHex) to listOf(
            LobbyMember(
                userProfile = UserProfile.Empty,
                publicKey = PublicKey("11111111111111111111111111111111"),
                enteredAt = Instant.fromEpochSeconds(1),
            )
        )
    )

    @Test
    fun `a read hosted group with someone waiting counts toward the badge`() = runTest {
        val feed = feedDelegate(listOf(groupHex to ChatType.GROUP), readThrough = 2, hostedGroups = setOf(groupHex))

        assertEquals(0, feed.observeUnreadChatListCount(flowOf(emptyMap())) { selfId }.first())
        assertEquals(1, feed.observeUnreadChatListCount(flowOf(waiting(groupHex))) { selfId }.first())
    }

    @Test
    fun `an unread hosted group with someone waiting counts once`() = runTest {
        val feed = feedDelegate(listOf(groupHex to ChatType.GROUP), hostedGroups = setOf(groupHex))

        assertEquals(1, feed.observeUnreadChatListCount(flowOf(waiting(groupHex))) { selfId }.first())
    }

    @Test
    fun `a lobby on a group the viewer doesn't host doesn't count`() = runTest {
        val feed = feedDelegate(listOf(groupHex to ChatType.GROUP), readThrough = 2)

        assertEquals(0, feed.observeUnreadChatListCount(flowOf(waiting(groupHex))) { selfId }.first())
    }
}
