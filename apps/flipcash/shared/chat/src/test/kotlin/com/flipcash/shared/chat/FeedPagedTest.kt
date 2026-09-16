package com.flipcash.shared.chat

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The paged feed reads the same rows from the same table as [FeedSyncDelegate.feed], through
 * Room's `PagingSource` instead of the whole list in state. The projection is shared, so what is
 * checked here is the wiring: rows come out as [ChatSummary], and a row the projection rejects
 * does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPagedTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val groupHex = "11223344"
    private val hiddenHex = "55667788"

    private fun entity(chatIdHex: String, isHidden: Boolean = false) = ChatMetadataEntity(
        chatIdHex = chatIdHex,
        chatType = ChatType.GROUP.name,
        lastActivityEpochMs = 2_000,
        lastMessageId = 2,
        isHidden = isHidden,
    )

    private val selfMember = ChatMember(
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

    private val lastMessage = ChatMessage(
        messageId = 2,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(2),
        unreadSeq = 2,
    )

    /** A `PagingSource` over a fixed list, standing in for the Room-generated one. */
    private class FixedPagingSource(
        private val rows: List<ChatMetadataEntity>,
    ) : PagingSource<Int, ChatMetadataEntity>() {
        override fun getRefreshKey(state: PagingState<Int, ChatMetadataEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatMetadataEntity> =
            LoadResult.Page(data = rows, prevKey = null, nextKey = null)
    }

    private fun delegate(rows: List<ChatMetadataEntity>): FeedSyncDelegate {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
            every { source.observeFeedPaged(any()) } returns FixedPagingSource(rows)
            every { source.toMetadata(any(), any(), any()) } answers {
                val row = firstArg<ChatMetadataEntity>()
                ChatMetadata(
                    chatId = ChatId(row.chatIdHex),
                    type = ChatType.valueOf(row.chatType),
                    members = secondArg(),
                    lastMessage = thirdArg(),
                    lastActivity = Instant.fromEpochMilliseconds(row.lastActivityEpochMs),
                    isHidden = row.isHidden,
                )
            }
        }

        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also {
            coEvery { it.getMembersForChat(any<String>()) } returns listOf(selfMember)
        }

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
            coEvery { it.getLatestVisible(any()) } returns lastMessage
        }

        // Explicit rather than relaxed: `Result` is a value class, so a relaxed mock hands the
        // mediator an Object it cannot cast, and the paging boundary call blows up.
        val chatController = mockk<ChatController>(relaxed = true).also {
            coEvery { it.getGroupChatFeed(any()) } returns
                Result.success(ChatFeedPage(chats = emptyList(), pagingToken = null, hasMore = false))
        }

        return FeedSyncDelegate(
            chatController = chatController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            stateHolder = ChatStateHolder(),
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )
    }

    @Test
    fun `a paged row comes out as a summary with its unread count`() = runTest {
        val page = delegate(listOf(entity(groupHex)))
            .feedPaged(ChatType.GROUP)
            .asSnapshot()

        assertEquals(1, page.size)
        assertEquals(ChatId(groupHex), page.single().metadata.chatId)
        assertEquals(1, page.single().unreadCount)
    }

    @Test
    fun `a hidden row is left out of the paged feed`() = runTest {
        val page = delegate(listOf(entity(hiddenHex, isHidden = true)))
            .feedPaged(ChatType.GROUP)
            .asSnapshot()

        assertEquals(emptyList(), page)
    }
}
