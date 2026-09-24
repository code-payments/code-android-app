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

    /** Read up to message 1 of 2, so each chat below has one unread message. */
    private val selfUnread = ChatMember(
        userId = selfId,
        userProfile = UserProfile.Empty,
        pointers = listOf(
            MessagePointer(
                type = PointerType.READ,
                userId = selfId,
                value = 1,
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

    private suspend fun TestScope.unreadChatListCount(vararg unread: Pair<String, ChatType>): Int {
        val entities = unread.map { (hex, type) -> entity(hex, type) }
        val members = unread.associate { (hex, type) ->
            hex to if (type == ChatType.GROUP) listOf(selfUnread) else listOf(selfUnread, addressableOther)
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
        return delegate.observeUnreadChatListCount().first()
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
}
