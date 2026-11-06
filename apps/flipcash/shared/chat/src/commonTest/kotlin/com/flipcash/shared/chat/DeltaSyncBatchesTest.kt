package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.GetDeltaError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.repository.DeltaUpdate
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `GetDelta` is a server stream: the catch-up arrives as a run of `MessageBatch`es of up to 100
 * messages each, and the cursor may only move to `latest_sequence` once the stream completes.
 */
class DeltaSyncBatchesTest {

    private val chat = ChatId("aabbccdd")

    private val written = mutableListOf<List<ChatMessage>>()
    private val cursors = mutableListOf<Long>()
    private var stored = 0L

    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
        coEvery { it.upsert(chat, any()) } answers { written += secondArg<List<ChatMessage>>() }
    }
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also {
        coEvery { it.getLatestEventSequence(chat) } answers { stored }
        coEvery { it.applyCatchUp(chat, any(), any()) } answers {
            val sequence = secondArg<Long>()
            if (sequence > 0) {
                cursors += sequence
                stored = maxOf(stored, sequence)
            }
        }
    }
    private val messagingController = mockk<ChatMessagingController>(relaxed = true)

    private val delegate = EventStreamDelegate(
        eventStreamingController = mockk<EventStreamingController>(relaxed = true).also {
            every { it.streamFailures } returns emptyFlow()
        },
        messagingController = messagingController,
        metadataDataSource = metadataDataSource,
        messageDataSource = messageDataSource,
        memberDataSource = mockk(relaxed = true),
        tokenCoordinator = mockk(relaxed = true),
        userManager = mockk<UserManager>(relaxed = true),
        stateHolder = ChatStateHolder(),
        analytics = mockk(relaxed = true),
        exchange = mockk(relaxed = true),
    )

    private fun message(sequence: Long) = ChatMessage(
        messageId = sequence,
        senderId = listOf(2),
        content = listOf(MessageContent.Text("m$sequence")),
        timestamp = Instant.fromEpochMilliseconds(sequence * 1000),
        unreadSeq = sequence,
        eventSequence = sequence,
    )

    /** [count] batches of [size] messages, sequences 1 through count * size. */
    private fun batches(count: Int, size: Int = 100): List<DeltaUpdate> {
        val head = (count * size).toLong()
        return (0 until count).map { b ->
            val first = b * size + 1L
            val last = first + size - 1
            DeltaUpdate(
                messages = (first..last).map(::message),
                latestSequence = head,
                checkpointSequence = last,
            )
        }
    }

    private fun stream(responses: List<DeltaUpdate>, thenFail: Throwable? = null): Flow<Result<DeltaUpdate>> = flow {
        for (response in responses) emit(Result.success(response))
        if (thenFail != null) throw thenFail
    }

    @Test
    fun `every batch in the stream is written and the cursor ends at the head`() = runTest {
        every { messagingController.getDelta(chat, 0L, any()) } returns stream(batches(3))

        delegate.performDeltaSync(chat)

        assertEquals((1L..300L).toList(), written.flatten().map { it.messageId }.sorted())
        assertEquals(300L, stored)
    }

    @Test
    fun `a stream that drops keeps what it delivered and resumes from the last checkpoint`() = runTest {
        every { messagingController.getDelta(chat, 0L, any()) } returns
            stream(batches(3).take(2), thenFail = RuntimeException("stream reset"))

        delegate.performDeltaSync(chat)

        assertEquals((1L..200L).toList(), written.flatten().map { it.messageId }.sorted())
        assertEquals(200L, stored, "the cursor must not move past what was applied")
    }

    @Test
    fun `a reset after the first batch keeps that batch and does not jump to the head`() = runTest {
        val responses = batches(3)
        every { messagingController.getDelta(chat, 0L, any()) } returns flow {
            emit(Result.success(responses[0]))
            emit(Result.failure(GetDeltaError.ResetRequired()))
            emit(Result.success(responses[1]))
        }

        delegate.performDeltaSync(chat)

        assertEquals((1L..100L).toList(), written.flatten().map { it.messageId }.sorted())
        assertEquals(100L, stored)
    }

    @Test
    fun `a long catch-up is written in fewer transactions than it has batches`() = runTest {
        every { messagingController.getDelta(chat, 0L, any()) } returns stream(batches(12))

        delegate.performDeltaSync(chat)

        assertEquals(1200, written.sumOf { it.size })
        assertTrue(written.size < 12, "wrote ${written.size} times for 12 batches")
        assertEquals(cursors.sorted(), cursors, "the cursor only moves forward")
        assertEquals(1200L, stored)
    }
}
