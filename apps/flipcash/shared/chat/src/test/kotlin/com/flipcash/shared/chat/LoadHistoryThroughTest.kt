package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.junit.Test

/**
 * A message link to history the device never stored. The walk pages back from the oldest stored
 * message, never fetching the target alone, so the transcript stays contiguous down to it.
 */
class LoadHistoryThroughTest {

    private val chatId = ChatId("aabbccdd")

    /** The server's history: ids 1..[newest]. */
    private val newest = 200L
    private val stored = sortedSetOf<Long>()
    private val requestedTokens = mutableListOf<Long?>()

    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also { source ->
        coEvery { source.getMessage(chatId, any()) } answers {
            val id = secondArg<Long>()
            if (id in stored) message(id) else null
        }
        coEvery { source.oldestMessageId(chatId) } answers { stored.firstOrNull() }
        coEvery { source.upsert(chatId, any()) } answers {
            stored += secondArg<List<ChatMessage>>().map { it.messageId }
        }
    }

    private val messagingController = mockk<ChatMessagingController>().also { controller ->
        val options = slot<QueryOptions>()
        coEvery { controller.getMessages(chatId, capture(options), any()) } answers {
            val before = options.captured.token?.let { ByteBuffer.wrap(it.toByteArray()).long }
            requestedTokens += before
            val from = (before ?: (newest + 1)) - 1
            Result.success(
                (from downTo maxOf(1, from - options.captured.limit + 1)).map { message(it) },
            )
        }
    }

    private val delegate = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = messagingController,
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = messageDataSource,
        memberDataSource = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        userManager = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
        senderResolver = mockk(relaxed = true),
    )

    private fun message(id: Long) = ChatMessage(
        messageId = id,
        senderId = listOf<Byte>(4, 5, 6),
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(id),
        unreadSeq = id,
    )

    @Test
    fun `a stored message needs no fetch`() = runTest {
        stored += 150L..200L

        assertTrue(delegate.loadHistoryThrough(chatId, 160))
        coVerify(exactly = 0) { messagingController.getMessages(any(), any(), any()) }
    }

    @Test
    fun `pages back from the oldest stored message until the target is stored`() = runTest {
        stored += 151L..200L

        assertTrue(delegate.loadHistoryThrough(chatId, 20))

        // Each page starts where the last one ended: no gap between the window and the target.
        assertEquals(listOf<Long?>(151, 101, 51), requestedTokens)
        assertEquals((1L..200L).toSortedSet(), stored)
    }

    @Test
    fun `nothing stored pages from the newest`() = runTest {
        assertTrue(delegate.loadHistoryThrough(chatId, 180))
        assertEquals(listOf<Long?>(null), requestedTokens)
    }

    @Test
    fun `a message older than everything stored that never came back is unreachable`() = runTest {
        stored += 151L..200L

        assertFalse(delegate.loadHistoryThrough(chatId, 0))
    }

    @Test
    fun `a message past the newest is unreachable`() = runTest {
        stored += 151L..200L

        assertFalse(delegate.loadHistoryThrough(chatId, 500))
        coVerify(exactly = 0) { messagingController.getMessages(any(), any(), any()) }
    }
}
