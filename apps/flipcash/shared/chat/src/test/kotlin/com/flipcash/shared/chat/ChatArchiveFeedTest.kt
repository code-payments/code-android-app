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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Rule 1: an archived chat leaves the list, every chip and the tab badge together, because they
 * all read the one feed the delegate filters. It appears only in the archived feed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatArchiveFeedTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val tipHex = "aabbccdd"
    private val groupHex = "11223344"

    private fun entity(hex: String, type: ChatType) = ChatMetadataEntity(
        chatIdHex = hex,
        chatType = type.name,
        lastActivityEpochMs = 2_000,
        lastMessageId = 2,
        isMember = true,
    )

    private fun message(id: Long) = ChatMessage(
        messageId = id,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(id),
        unreadSeq = id,
    )

    private val selfUnread = ChatMember(
        userId = selfId,
        userProfile = UserProfile.Empty,
        pointers = listOf(
            MessagePointer(PointerType.READ, selfId, value = 1, timestamp = Instant.fromEpochSeconds(1_000))
        ),
    )

    // A tip DM renders only when the other member is addressable, so give them a verified phone.
    private val tipOther = ChatMember(
        userId = otherId,
        userProfile = UserProfile.Empty.copy(
            displayName = "Ada",
            phoneNumber = VerifiableContactMethod("+15551234567", verified = true),
        ),
        pointers = emptyList(),
    )

    private class Fixture(val delegate: FeedSyncDelegate, val archived: MutableStateFlow<Set<ChatId>>)

    private fun TestScope.fixture(initiallyArchived: Set<ChatId>): Fixture {
        val entities = listOf(entity(tipHex, ChatType.TIP_DM), entity(groupHex, ChatType.GROUP))
        val members = mapOf(tipHex to listOf(selfUnread, tipOther), groupHex to listOf(selfUnread))

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

        val archived = MutableStateFlow(initiallyArchived)
        val archiveStore = object : ChatArchiveStore by ChatArchiveStore.None {
            override fun observeArchived() = archived
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
            archiveStore = archiveStore,
        )
        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        return Fixture(delegate, archived)
    }

    private val listTypes = arrayOf(ChatType.TIP_DM, ChatType.GROUP)

    @Test
    fun `an archived chat is not in the feed, the current feed or the badge`() = runTest {
        val f = fixture(setOf(ChatId(groupHex)))
        assertEquals(listOf(ChatId(tipHex)), f.delegate.feed(*listTypes).first().map { it.metadata.chatId })
        assertEquals(listOf(ChatId(tipHex)), f.delegate.currentFeed(*listTypes)!!.map { it.metadata.chatId })
        assertEquals(1, f.delegate.observeUnreadChatListCount().first())
    }

    @Test
    fun `an archived chat is in the archived feed and nowhere else`() = runTest {
        val f = fixture(setOf(ChatId(groupHex)))
        assertEquals(listOf(ChatId(groupHex)), f.delegate.archivedFeed(*listTypes).first().map { it.metadata.chatId })
        assertEquals(listOf(ChatId(groupHex)), f.delegate.currentArchivedFeed(*listTypes)!!.map { it.metadata.chatId })
    }

    @Test
    fun `unarchiving puts the chat back in the main feed`() = runTest {
        val f = fixture(setOf(ChatId(groupHex)))
        f.archived.value = emptySet()
        runCurrent()
        assertEquals(2, f.delegate.currentFeed(*listTypes)!!.size)
        assertEquals(emptyList(), f.delegate.currentArchivedFeed(*listTypes)!!)
    }

    @Test
    fun `with nothing archived the feed is unchanged`() = runTest {
        val f = fixture(emptySet())
        assertEquals(2, f.delegate.currentFeed(*listTypes)!!.size)
        assertEquals(2, f.delegate.observeUnreadChatListCount().first())
    }
}
