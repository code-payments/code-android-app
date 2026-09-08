package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.PendingMessage
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.EditMessageError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The optimistic mutation round trip through the delegate: the overlay goes up before the request,
 * and comes down on the answer — after the server's row is stored on success, and without one on
 * failure, which is what makes a failed delete put the message back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagingMutationTest {

    private val chatId = ChatId("aabbccdd")
    private val selfId = listOf<Byte>(1, 2, 3)

    private val stored = ChatMessage(
        messageId = 1,
        senderId = selfId,
        content = listOf(MessageContent.Text("before")),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 0,
        eventSequence = 4,
        isFromSelf = true,
    )

    private val confirmed = stored.copy(
        content = listOf(MessageContent.Text("after")),
        lastEditedTs = Instant.fromEpochSeconds(2_000),
        eventSequence = 5,
    )

    private fun delegateWith(
        messagingController: ChatMessagingController,
        messageDataSource: ChatMessageDataSource,
    ): MessagingDelegate {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.accountId } returns selfId
        return MessagingDelegate(
            chatController = mockk(relaxed = true),
            messagingController = messagingController,
            metadataDataSource = mockk(relaxed = true),
            messageDataSource = messageDataSource,
            memberDataSource = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
        )
    }

    private fun dataSource(): ChatMessageDataSource {
        val source = mockk<ChatMessageDataSource>(relaxed = true)
        coEvery { source.getMessage(chatId, 1) } returns stored
        return source
    }

    @Test
    fun `an edit is shown before the server answers, and stored before the overlay comes down`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = mockk<ChatMessagingController>(relaxed = true)
        coEvery { controller.editMessage(chatId, 1, any(), 4) } coAnswers {
            gate.await()
            Result.success(confirmed)
        }
        val source = dataSource()
        val delegate = delegateWith(controller, source)

        val request = launch { delegate.editMessage(chatId, messageId = 1, text = "after") }
        runCurrent()

        val inFlight = delegate.observePendingMutations(chatId).first()
        assertEquals(setOf(1L), inFlight.keys)
        assertEquals(4, inFlight.getValue(1L).expectedSequence)
        assertEquals("after", assertIs<PendingMutation.Kind.Edited>(inFlight.getValue(1L).kind).text)

        gate.complete(Unit)
        request.join()

        coVerify(exactly = 1) { source.upsert(chatId, listOf(confirmed)) }
        assertTrue(delegate.observePendingMutations(chatId).first().isEmpty())
    }

    @Test
    fun `the edit body is built from the stored content, not from a bare text message`() = runTest {
        val replied = stored.copy(
            content = listOf(
                MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("before"))),
            ),
        )
        val controller = mockk<ChatMessagingController>(relaxed = true)
        coEvery { controller.editMessage(any(), any(), any(), any()) } returns Result.success(confirmed)
        val source = mockk<ChatMessageDataSource>(relaxed = true)
        coEvery { source.getMessage(chatId, 1) } returns replied

        delegateWith(controller, source).editMessage(chatId, messageId = 1, text = "after")

        coVerify(exactly = 1) {
            controller.editMessage(
                chatId,
                1,
                listOf(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("after")))),
                4,
            )
        }
    }

    @Test
    fun `a failed delete puts the message back`() = runTest {
        val controller = mockk<ChatMessagingController>(relaxed = true)
        coEvery { controller.deleteMessage(chatId, 1, 4) } returns Result.failure(EditMessageError.Denied())
        val source = dataSource()
        val delegate = delegateWith(controller, source)

        val result = delegate.deleteMessage(chatId, messageId = 1)

        assertTrue(result.isFailure)
        assertTrue(delegate.observePendingMutations(chatId).first().isEmpty())
        coVerify(exactly = 0) { source.upsert(chatId, any()) }
    }

    @Test
    fun `a conflicted edit re-reads the server's copy instead of retrying`() = runTest {
        val serverCopy = confirmed.copy(content = listOf(MessageContent.Text("someone else's edit")))
        val controller = mockk<ChatMessagingController>(relaxed = true)
        coEvery { controller.editMessage(chatId, 1, any(), 4) } returns
            Result.failure(EditMessageError.Conflict())
        coEvery { controller.getMessage(chatId, 1) } returns Result.success(serverCopy)
        val source = dataSource()
        val delegate = delegateWith(controller, source)

        val result = delegate.editMessage(chatId, messageId = 1, text = "after")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { controller.editMessage(chatId, 1, any(), 4) }
        coVerify(exactly = 1) { source.upsert(chatId, listOf(serverCopy)) }
        assertTrue(delegate.observePendingMutations(chatId).first().isEmpty())
    }

    @Test
    fun `an edit to blank is refused before anything is sent`() = runTest {
        val controller = mockk<ChatMessagingController>(relaxed = true)
        val source = dataSource()
        val delegate = delegateWith(controller, source)

        val result = delegate.editMessage(chatId, messageId = 1, text = "   ")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { controller.editMessage(any(), any(), any(), any()) }
        assertTrue(delegate.observePendingMutations(chatId).first().isEmpty())
    }

    /**
     * A sender that cites a message wraps the body rather than sending alongside it: the proto
     * carries one content list, and the reply *is* the message.
     */
    @Test
    fun `a reply wraps the text in Reply content carrying the target id`() = runTest {
        // Relaxed: sendMessage's success path advances the read pointer through this same
        // controller, which is not what these tests are asserting on.
        val controller = mockk<ChatMessagingController>(relaxed = true)
        val dataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val sentContent = slot<List<MessageContent>>()
        coEvery { dataSource.insertPending(any(), any(), any()) } returns
            PendingMessage(message = stored, clientMessageId = ClientMessageId(byteArrayOf(9)))
        coEvery { controller.sendMessage(any(), capture(sentContent), any()) } returns
            Result.success(stored)

        delegateWith(controller, dataSource).sendMessage(chatId, "sure", replyToMessageId = 7)

        val reply = assertIs<MessageContent.Reply>(sentContent.captured.single())
        assertEquals(7L, reply.repliedMessageId)
        assertEquals(listOf(MessageContent.Text("sure")), reply.content)
    }

    @Test
    fun `an ordinary message is still bare text`() = runTest {
        // Relaxed: sendMessage's success path advances the read pointer through this same
        // controller, which is not what these tests are asserting on.
        val controller = mockk<ChatMessagingController>(relaxed = true)
        val dataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val sentContent = slot<List<MessageContent>>()
        coEvery { dataSource.insertPending(any(), any(), any()) } returns
            PendingMessage(message = stored, clientMessageId = ClientMessageId(byteArrayOf(9)))
        coEvery { controller.sendMessage(any(), capture(sentContent), any()) } returns
            Result.success(stored)

        delegateWith(controller, dataSource).sendMessage(chatId, "hello")

        assertEquals(listOf(MessageContent.Text("hello")), sentContent.captured)
    }

    /**
     * The optimistic row carries the same payload as the request, so the quote is on screen before
     * the server answers rather than appearing when it does.
     */
    @Test
    fun `the optimistic row carries the reply too`() = runTest {
        // Relaxed: sendMessage's success path advances the read pointer through this same
        // controller, which is not what these tests are asserting on.
        val controller = mockk<ChatMessagingController>(relaxed = true)
        val dataSource = mockk<ChatMessageDataSource>(relaxed = true)
        val pendingContent = slot<List<MessageContent>>()
        coEvery { dataSource.insertPending(any(), capture(pendingContent), any()) } returns
            PendingMessage(message = stored, clientMessageId = ClientMessageId(byteArrayOf(9)))
        coEvery { controller.sendMessage(any(), any(), any()) } returns Result.success(stored)

        delegateWith(controller, dataSource).sendMessage(chatId, "sure", replyToMessageId = 7)

        assertIs<MessageContent.Reply>(pendingContent.captured.single())
    }
}
