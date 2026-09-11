package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
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
 * Covers the write a push-carried message performs in place of a fetch.
 *
 * Three properties matter here, and none of them are visible from the planner: the write
 * reaches the same data source a fetched page would, the event cursor stays where it was,
 * and the feed row only moves forward.
 */
class MessagingPushedMessageTest {

    private val chatId = ChatId("aabbccdd")

    private fun message(id: Long, eventSequence: Long, epochSeconds: Long = 1_757_000_000) =
        ChatMessage(
            messageId = id,
            senderId = listOf<Byte>(4, 5, 6),
            content = listOf(MessageContent.Text("msg-$id")),
            timestamp = Instant.fromEpochSeconds(epochSeconds),
            unreadSeq = 0,
            eventSequence = eventSequence,
        )

    private fun delegateWith(
        metadataDataSource: ChatMetadataDataSource,
        messageDataSource: ChatMessageDataSource = mockk(relaxed = true),
        messagingController: ChatMessagingController = mockk(relaxed = true),
    ) = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = messagingController,
        metadataDataSource = metadataDataSource,
        messageDataSource = messageDataSource,
        memberDataSource = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        userManager = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
    )

    @Test
    fun `a pushed message is written through the same source a fetched page uses`() = runTest {
        val pushed = message(id = 12, eventSequence = 9)
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 11

        delegateWith(metadataDataSource, messageDataSource).applyPushedMessage(chatId, pushed)

        coVerify(exactly = 1) { messageDataSource.upsert(chatId, listOf(pushed)) }
    }

    @Test
    fun `applying a pushed message makes no network call`() = runTest {
        val messagingController = mockk<ChatMessagingController>(relaxed = true)
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 0

        delegateWith(metadataDataSource, messagingController = messagingController)
            .applyPushedMessage(chatId, message(id = 1, eventSequence = 3))

        coVerify(exactly = 0) { messagingController.getMessages(any(), any()) }
    }

    @Test
    fun `a pushed message does not seat the event cursor`() = runTest {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 0

        delegateWith(metadataDataSource).applyPushedMessage(chatId, message(id = 12, eventSequence = 99))

        // A push carries one message, not a page. Seating the cursor at its sequence would let a
        // later catch-up resume from a frontier it never fetched.
        coVerify(exactly = 0) { metadataDataSource.updateLatestEventSequence(chatId, any()) }
    }

    @Test
    fun `a newer pushed message moves the feed row forward`() = runTest {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 11

        delegateWith(metadataDataSource)
            .applyPushedMessage(chatId, message(id = 12, eventSequence = 9, epochSeconds = 1_757_000_042))

        coVerify(exactly = 1) { metadataDataSource.updateLastMessageId(chatId, 12) }
        coVerify(exactly = 1) { metadataDataSource.updateLastActivity(chatId, 1_757_000_042_000) }
    }

    @Test
    fun `a re-delivered push does not rewind the feed row`() = runTest {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 20

        delegateWith(metadataDataSource).applyPushedMessage(chatId, message(id = 12, eventSequence = 9))

        coVerify(exactly = 0) { metadataDataSource.updateLastMessageId(chatId, any()) }
        coVerify(exactly = 0) { metadataDataSource.updateLastActivity(chatId, any()) }
    }

    @Test
    fun `the same push applied twice moves the feed row once`() = runTest {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns 11 andThen 12
        val pushed = message(id = 12, eventSequence = 9)
        val delegate = delegateWith(metadataDataSource)

        delegate.applyPushedMessage(chatId, pushed)
        delegate.applyPushedMessage(chatId, pushed)

        coVerify(exactly = 1) { metadataDataSource.updateLastMessageId(chatId, 12) }
    }

    @Test
    fun `a chat with no stored message id accepts the first push`() = runTest {
        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLastMessageId(chatId) } returns null

        delegateWith(metadataDataSource).applyPushedMessage(chatId, message(id = 1, eventSequence = 3))

        coVerify(exactly = 1) { metadataDataSource.updateLastMessageId(chatId, 1) }
    }
}
