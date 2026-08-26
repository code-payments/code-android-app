package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * Covers the event cursor a newest-page load leaves behind. The cursor is what marks a
 * transcript as fetched — a load that does not seat it makes every later feed sync re-fetch
 * the same page — and it must only ever move forward.
 */
class MessagingLoadCursorTest {

    private val chatId = ChatId("aabbccdd")

    private fun message(id: Long, eventSequence: Long) = ChatMessage(
        messageId = id,
        senderId = listOf<Byte>(4, 5, 6),
        content = listOf(MessageContent.Text("msg-$id")),
        timestamp = Instant.fromEpochSeconds(1000 + id),
        unreadSeq = 0,
        eventSequence = eventSequence,
    )

    private fun delegateWith(
        messagingController: ChatMessagingController,
        metadataDataSource: ChatMetadataDataSource,
    ) = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = messagingController,
        metadataDataSource = metadataDataSource,
        messageDataSource = mockk(relaxed = true),
        memberDataSource = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        userManager = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
    )

    @Test
    fun `newest page seats the cursor at its highest event sequence`() = runTest {
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        coEvery { messagingController.getMessages(chatId, any()) } returns
            Result.success(listOf(message(id = 1, eventSequence = 7), message(id = 2, eventSequence = 9)))
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 0

        delegateWith(messagingController, metadataDataSource).loadMessages(chatId)

        coVerify(exactly = 1) { metadataDataSource.updateLatestEventSequence(chatId, 9) }
    }

    @Test
    fun `page older than the cursor does not rewind it`() = runTest {
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        coEvery { messagingController.getMessages(chatId, any()) } returns
            Result.success(listOf(message(id = 1, eventSequence = 4)))
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 9

        delegateWith(messagingController, metadataDataSource).loadMessages(chatId)

        coVerify(exactly = 0) { metadataDataSource.updateLatestEventSequence(chatId, any()) }
    }

    @Test
    fun `empty page leaves the cursor unseated`() = runTest {
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        coEvery { messagingController.getMessages(chatId, any()) } returns Result.success(emptyList())
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLatestEventSequence(chatId) } returns 0

        delegateWith(messagingController, metadataDataSource).loadMessages(chatId)

        coVerify(exactly = 0) { metadataDataSource.updateLatestEventSequence(chatId, any()) }
    }
}
