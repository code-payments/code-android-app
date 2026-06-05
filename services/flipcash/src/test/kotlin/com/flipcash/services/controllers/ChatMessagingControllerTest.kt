package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.repository.MessagingRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MessagingControllerTest {

    private val repository = FakeMessagingRepository()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = MessagingController(repository, userManager)

    private val testChatId = ChatId(ByteArray(32) { 0x01 })

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    private fun stubMessage(id: Long = 1, text: String = "hello") = ChatMessage(
        messageId = id,
        senderId = listOf(1.toByte()),
        content = listOf(MessageContent.Text(text)),
        timestamp = Instant.fromEpochSeconds(1000),
        unreadSeq = id,
    )

    // region getMessage

    @Test
    fun `getMessage fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.getMessage(testChatId, 1)
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `getMessage forwards chatId and messageId`() = runTest {
        stubOwner()
        val msg = stubMessage(42)
        repository.getMessageResult = Result.success(msg)

        controller.getMessage(testChatId, 42)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(42L, repository.lastMessageId)
    }

    @Test
    fun `getMessage returns the message from repository`() = runTest {
        stubOwner()
        val msg = stubMessage(1, "world")
        repository.getMessageResult = Result.success(msg)

        val result = controller.getMessage(testChatId, 1)

        assertEquals("world", (result.getOrThrow().content.first() as MessageContent.Text).text)
    }

    // endregion

    // region sendMessage

    @Test
    fun `sendMessage fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.sendMessage(testChatId, listOf(MessageContent.Text("hi")), ClientMessageId(ByteArray(16)))
        assertTrue(result.isFailure)
    }

    @Test
    fun `sendMessage forwards content and clientMessageId`() = runTest {
        stubOwner()
        val clientId = ClientMessageId(ByteArray(16) { 0xAB.toByte() })
        val content = listOf(MessageContent.Text("test"))
        repository.sendMessageResult = Result.success(stubMessage())

        controller.sendMessage(testChatId, content, clientId)

        assertEquals(content, repository.lastContent)
        assertEquals(clientId, repository.lastClientMessageId)
    }

    @Test
    fun `sendMessage returns server-assigned message`() = runTest {
        stubOwner()
        val serverMsg = stubMessage(99, "confirmed")
        repository.sendMessageResult = Result.success(serverMsg)

        val result = controller.sendMessage(testChatId, listOf(MessageContent.Text("test")), ClientMessageId(ByteArray(16)))

        assertEquals(99L, result.getOrThrow().messageId)
    }

    // endregion

    // region advancePointer

    @Test
    fun `advancePointer forwards pointer type and message id`() = runTest {
        stubOwner()
        repository.advancePointerResult = Result.success(Unit)

        controller.advancePointer(testChatId, PointerType.READ, 50)

        assertEquals(PointerType.READ, repository.lastPointerType)
        assertEquals(50L, repository.lastMessageId)
    }

    @Test
    fun `advancePointer surfaces repository failure`() = runTest {
        stubOwner()
        val cause = RuntimeException("not found")
        repository.advancePointerResult = Result.failure(cause)

        val result = controller.advancePointer(testChatId, PointerType.DELIVERED, 10)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region notifyIsTyping

    @Test
    fun `notifyIsTyping forwards typing state`() = runTest {
        stubOwner()
        repository.notifyIsTypingResult = Result.success(Unit)

        controller.notifyIsTyping(testChatId, TypingState.STARTED_TYPING)

        assertEquals(TypingState.STARTED_TYPING, repository.lastTypingState)
    }

    // endregion

    // region getMessages

    @Test
    fun `getMessages uses default QueryOptions`() = runTest {
        stubOwner()
        repository.getMessagesResult = Result.success(emptyList())

        controller.getMessages(testChatId)

        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getMessagesByIds forwards message ids`() = runTest {
        stubOwner()
        val ids = listOf(1L, 2L, 3L)
        repository.getMessagesByIdsResult = Result.success(listOf(stubMessage(1), stubMessage(2), stubMessage(3)))

        val result = controller.getMessagesByIds(testChatId, ids)

        assertEquals(ids, repository.lastMessageIds)
        assertEquals(3, result.getOrThrow().size)
    }

    // endregion
}

// region Fakes

private class FakeMessagingRepository : MessagingRepository {
    var getMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var getMessagesResult: Result<List<ChatMessage>> = Result.failure(RuntimeException("not configured"))
    var getMessagesByIdsResult: Result<List<ChatMessage>> = Result.failure(RuntimeException("not configured"))
    var sendMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var advancePointerResult: Result<Unit> = Result.failure(RuntimeException("not configured"))
    var notifyIsTypingResult: Result<Unit> = Result.failure(RuntimeException("not configured"))

    var lastChatId: ChatId? = null
    var lastMessageId: Long? = null
    var lastMessageIds: List<Long>? = null
    var lastQueryOptions: QueryOptions? = null
    var lastContent: List<MessageContent>? = null
    var lastClientMessageId: ClientMessageId? = null
    var lastPointerType: PointerType? = null
    var lastTypingState: TypingState? = null

    override suspend fun getMessage(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long): Result<ChatMessage> {
        lastChatId = chatId; lastMessageId = messageId
        return getMessageResult
    }

    override suspend fun getMessages(owner: Ed25519.KeyPair, chatId: ChatId, queryOptions: QueryOptions): Result<List<ChatMessage>> {
        lastChatId = chatId; lastQueryOptions = queryOptions
        return getMessagesResult
    }

    override suspend fun getMessagesByIds(owner: Ed25519.KeyPair, chatId: ChatId, messageIds: List<Long>): Result<List<ChatMessage>> {
        lastChatId = chatId; lastMessageIds = messageIds
        return getMessagesByIdsResult
    }

    override suspend fun sendMessage(owner: Ed25519.KeyPair, chatId: ChatId, content: List<MessageContent>, clientMessageId: ClientMessageId): Result<ChatMessage> {
        lastChatId = chatId; lastContent = content; lastClientMessageId = clientMessageId
        return sendMessageResult
    }

    override suspend fun advancePointer(owner: Ed25519.KeyPair, chatId: ChatId, pointerType: PointerType, messageId: Long): Result<Unit> {
        lastChatId = chatId; lastPointerType = pointerType; lastMessageId = messageId
        return advancePointerResult
    }

    override suspend fun notifyIsTyping(owner: Ed25519.KeyPair, chatId: ChatId, state: TypingState): Result<Unit> {
        lastChatId = chatId; lastTypingState = state
        return notifyIsTypingResult
    }
}

// endregion
