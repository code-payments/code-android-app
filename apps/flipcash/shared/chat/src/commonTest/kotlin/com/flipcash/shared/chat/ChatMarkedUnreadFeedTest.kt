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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The unread mark rides the same combine as archive: a marked chat reads as unread in the feed and
 * the tab badge, and its real count still shows when it has one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMarkedUnreadFeedTest {

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

    private val selfRead = selfUnread.copy(
        pointers = listOf(
            MessagePointer(PointerType.READ, selfId, value = 2, timestamp = Instant.fromEpochSeconds(1_000))
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

    private class Fixture(
        val delegate: FeedSyncDelegate,
        val archived: MutableStateFlow<Set<ChatId>>,
        val marked: MutableStateFlow<Set<ChatId>>,
    )

    private fun TestScope.fixture(initiallyArchived: Set<ChatId> = emptySet(), initiallyMarked: Set<ChatId> = emptySet()): Fixture {
        val entities = listOf(entity(tipHex, ChatType.TIP_DM), entity(groupHex, ChatType.GROUP))
        val members = mapOf(tipHex to listOf(selfRead, tipOther), groupHex to listOf(selfUnread))

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

        val marked = MutableStateFlow(initiallyMarked)
        val markStore = object : ChatMarkedUnreadStore by ChatMarkedUnreadStore.None {
            override fun observeMarked() = marked
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
            markedUnreadStore = markStore,
        )
        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        return Fixture(delegate, archived, marked)
    }

    private val listTypes = arrayOf(ChatType.TIP_DM, ChatType.GROUP)
    private val tip = ChatId(tipHex)
    private val group = ChatId(groupHex)

    private suspend fun FeedSyncDelegate.summary(id: ChatId) =
        feed(*listTypes).first().single { it.metadata.chatId == id }

    // Test case 1: the read tip DM, once marked, is unread with no count and lifts the badge by one.
    @Test
    fun `marking a read chat makes it unread with no count and adds one to the badge`() = runTest {
        val f = fixture()
        assertEquals(1, f.delegate.observeUnreadChatListCount().first())
        assertFalse(f.delegate.summary(tip).isUnread)

        f.marked.value = setOf(tip)
        runCurrent()

        val summary = f.delegate.summary(tip)
        assertEquals(0, summary.unreadCount)
        assertTrue(summary.markedUnread)
        assertTrue(summary.isUnread)
        assertEquals(2, f.delegate.observeUnreadChatListCount().first())
    }

    // Test case 5: the group has one unread message and a mark; the count shows, the badge counts it once.
    @Test
    fun `a marked chat with unread messages keeps its count and counts once`() = runTest {
        val f = fixture(initiallyMarked = setOf(group))
        val summary = f.delegate.summary(group)
        assertEquals(1, summary.unreadCount)
        assertTrue(summary.markedUnread)
        assertEquals(1, f.delegate.observeUnreadChatListCount().first())
        assertEquals(1, f.delegate.observeUnreadChatListCount(flowOf(emptyMap())) { selfId }.first())
    }

    // Test case 6: archive and unarchive leave the mark alone.
    @Test
    fun `a marked chat stays marked through archive and unarchive`() = runTest {
        val f = fixture(initiallyMarked = setOf(tip))

        f.archived.value = setOf(tip)
        runCurrent()
        assertTrue(f.delegate.archivedFeed(*listTypes).first().single { it.metadata.chatId == tip }.markedUnread)
        // An archived chat leaves the badge, mark or not.
        assertEquals(1, f.delegate.observeUnreadChatListCount().first())

        f.archived.value = emptySet()
        runCurrent()
        assertTrue(f.delegate.summary(tip).markedUnread)
        assertEquals(2, f.delegate.observeUnreadChatListCount().first())
    }
}
