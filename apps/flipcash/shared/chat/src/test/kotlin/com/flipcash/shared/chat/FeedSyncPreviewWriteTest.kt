package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.Test

/**
 * Room invalidates `chat_messages` as a whole, so every write re-pages whichever transcript is
 * open. A feed sync carries one preview per chat and must land them in a single write.
 */
class FeedSyncPreviewWriteTest {

    private fun message(messageId: Long) = ChatMessage(
        messageId = messageId,
        senderId = listOf<Byte>(4, 5, 6),
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
    )

    private fun metadata(hex: String, lastMessage: ChatMessage?) = ChatMetadata(
        chatId = ChatId(hex),
        type = ChatType.CONTACT_DM,
        members = emptyList(),
        lastMessage = lastMessage,
        lastActivity = Instant.fromEpochSeconds(1000),
    )

    @Test
    fun `feed sync writes every chat's preview in one batch`() = runTest {
        val chats = listOf(
            metadata("aa", message(1)),
            metadata("bb", message(2)),
            metadata("cc", lastMessage = null),
        )
        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(chats, null, false))
        coEvery { chatController.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.success(ChatFeedPage(emptyList(), null, false))
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        FeedSyncDelegate(
            messagingController = mockk(relaxed = true),
            chatController = chatController,
            metadataDataSource = mockk(relaxed = true),
            messageDataSource = messageDataSource,
            memberDataSource = mockk(relaxed = true),
            stateHolder = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
        ).performFeedSync()

        coVerify(exactly = 1) {
            messageDataSource.prepare(
                mapOf(
                    ChatId("aa") to listOf(message(1)),
                    ChatId("bb") to listOf(message(2)),
                ),
            )
        }
        coVerify(exactly = 0) { messageDataSource.upsert(any<ChatId>(), any()) }
    }
}
