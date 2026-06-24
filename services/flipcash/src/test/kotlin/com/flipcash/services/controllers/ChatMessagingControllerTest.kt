package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.repository.ChatMessagingRepository
import com.flipcash.services.repository.DeltaUpdate
import com.flipcash.services.repository.ReactorsPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class ChatMessagingControllerTest {

    private val repository = FakeChatMessagingRepository()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = ChatMessagingController(repository, userManager)

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

    private fun stubReaction(emoji: Emoji, count: Long = 1) = EmojiReaction(
        emoji = emoji,
        count = count,
        reactedBySelf = false,
        sampleReactors = emptyList(),
        sequence = 1,
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

    // region editMessage

    @Test
    fun `editMessage fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.editMessage(testChatId, 1, listOf(MessageContent.Text("edited")), 5)
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `editMessage forwards parameters`() = runTest {
        stubOwner()
        val content = listOf(MessageContent.Text("edited"))
        repository.editMessageResult = Result.success(stubMessage(1, "edited"))

        controller.editMessage(testChatId, 1, content, 5)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(1L, repository.lastMessageId)
        assertEquals(content, repository.lastContent)
        assertEquals(5L, repository.lastExpectedEventSequence)
    }

    @Test
    fun `editMessage returns updated message`() = runTest {
        stubOwner()
        val edited = stubMessage(1, "edited")
        repository.editMessageResult = Result.success(edited)

        val result = controller.editMessage(testChatId, 1, listOf(MessageContent.Text("edited")), 5)

        assertEquals("edited", (result.getOrThrow().content.first() as MessageContent.Text).text)
    }

    // endregion

    // region deleteMessage

    @Test
    fun `deleteMessage fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.deleteMessage(testChatId, 1, 5)
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `deleteMessage forwards parameters`() = runTest {
        stubOwner()
        repository.deleteMessageResult = Result.success(stubMessage(1))

        controller.deleteMessage(testChatId, 1, 5)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(1L, repository.lastMessageId)
        assertEquals(5L, repository.lastExpectedEventSequence)
    }

    @Test
    fun `deleteMessage surfaces repository failure`() = runTest {
        stubOwner()
        val cause = RuntimeException("not found")
        repository.deleteMessageResult = Result.failure(cause)

        val result = controller.deleteMessage(testChatId, 1, 5)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region addReaction

    @Test
    fun `addReaction fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.addReaction(testChatId, 1, Emoji("\uD83D\uDC4D"))
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `addReaction forwards parameters`() = runTest {
        stubOwner()
        val emoji = Emoji("\uD83D\uDC4D")
        repository.addReactionResult = Result.success(stubReaction(emoji))

        controller.addReaction(testChatId, 1, emoji)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(1L, repository.lastMessageId)
        assertEquals(emoji, repository.lastEmoji)
    }

    @Test
    fun `addReaction returns reaction from repository`() = runTest {
        stubOwner()
        val emoji = Emoji("\uD83D\uDC4D")
        val reaction = stubReaction(emoji, count = 3)
        repository.addReactionResult = Result.success(reaction)

        val result = controller.addReaction(testChatId, 1, emoji)

        assertEquals(3L, result.getOrThrow().count)
        assertEquals(emoji, result.getOrThrow().emoji)
    }

    // endregion

    // region removeReaction

    @Test
    fun `removeReaction fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.removeReaction(testChatId, 1, Emoji("\uD83D\uDC4D"))
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `removeReaction forwards parameters`() = runTest {
        stubOwner()
        val emoji = Emoji("\uD83D\uDE00")
        repository.removeReactionResult = Result.success(stubReaction(emoji, count = 0))

        controller.removeReaction(testChatId, 1, emoji)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(1L, repository.lastMessageId)
        assertEquals(emoji, repository.lastEmoji)
    }

    // endregion

    // region getReactors

    @Test
    fun `getReactors fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.getReactors(testChatId, 1, Emoji("\uD83D\uDC4D"))
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `getReactors forwards parameters with default QueryOptions`() = runTest {
        stubOwner()
        val emoji = Emoji("\uD83D\uDC4D")
        repository.getReactorsResult = Result.success(ReactorsPage(emptyList(), hasMore = false))

        controller.getReactors(testChatId, 1, emoji)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(1L, repository.lastMessageId)
        assertEquals(emoji, repository.lastEmoji)
        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getReactors returns page from repository`() = runTest {
        stubOwner()
        val emoji = Emoji("\uD83D\uDC4D")
        val page = ReactorsPage(
            reactors = listOf(Reactor(userId = listOf(1.toByte()), reactedAt = Instant.fromEpochSeconds(1000))),
            hasMore = true,
        )
        repository.getReactorsResult = Result.success(page)

        val result = controller.getReactors(testChatId, 1, emoji)

        assertEquals(1, result.getOrThrow().reactors.size)
        assertTrue(result.getOrThrow().hasMore)
    }

    // endregion

    // region getReactionSummary

    @Test
    fun `getReactionSummary fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.getReactionSummary(testChatId, 1)
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `getReactionSummary forwards chatId and messageId`() = runTest {
        stubOwner()
        repository.getReactionSummaryResult = Result.success(ReactionSummary(messageId = 42, reactions = emptyList()))

        controller.getReactionSummary(testChatId, 42)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(42L, repository.lastMessageId)
    }

    @Test
    fun `getReactionSummary returns summary from repository`() = runTest {
        stubOwner()
        val summary = ReactionSummary(
            messageId = 1,
            reactions = listOf(stubReaction(Emoji("\uD83D\uDC4D"), count = 5)),
        )
        repository.getReactionSummaryResult = Result.success(summary)

        val result = controller.getReactionSummary(testChatId, 1)

        assertEquals(1, result.getOrThrow().reactions.size)
        assertEquals(5L, result.getOrThrow().reactions.first().count)
    }

    // endregion

    // region getReactionSummaries

    @Test
    fun `getReactionSummaries fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = controller.getReactionSummaries(testChatId)
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `getReactionSummaries uses default QueryOptions`() = runTest {
        stubOwner()
        repository.getReactionSummariesResult = Result.success(emptyList())

        controller.getReactionSummaries(testChatId)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getReactionSummaries returns list from repository`() = runTest {
        stubOwner()
        val summaries = listOf(
            ReactionSummary(messageId = 1, reactions = listOf(stubReaction(Emoji("\uD83D\uDC4D")))),
            ReactionSummary(messageId = 2, reactions = emptyList()),
        )
        repository.getReactionSummariesResult = Result.success(summaries)

        val result = controller.getReactionSummaries(testChatId)

        assertEquals(2, result.getOrThrow().size)
    }

    // endregion

    // region getDelta

    @Test
    fun `getDelta fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null
        val result = runCatching { controller.getDelta(testChatId, 0) }
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `getDelta forwards chatId and afterSequence`() = runTest {
        stubOwner()

        controller.getDelta(testChatId, 10)

        assertEquals(testChatId, repository.lastChatId)
        assertEquals(10L, repository.lastAfterSequence)
    }

    // endregion
}

// region Fakes

private class FakeChatMessagingRepository : ChatMessagingRepository {
    var getMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var getMessagesResult: Result<List<ChatMessage>> = Result.failure(RuntimeException("not configured"))
    var getMessagesByIdsResult: Result<List<ChatMessage>> = Result.failure(RuntimeException("not configured"))
    var sendMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var editMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var deleteMessageResult: Result<ChatMessage> = Result.failure(RuntimeException("not configured"))
    var addReactionResult: Result<EmojiReaction> = Result.failure(RuntimeException("not configured"))
    var removeReactionResult: Result<EmojiReaction> = Result.failure(RuntimeException("not configured"))
    var getReactorsResult: Result<ReactorsPage> = Result.failure(RuntimeException("not configured"))
    var getReactionSummaryResult: Result<ReactionSummary> = Result.failure(RuntimeException("not configured"))
    var getReactionSummariesResult: Result<List<ReactionSummary>> = Result.failure(RuntimeException("not configured"))
    var advancePointerResult: Result<Unit> = Result.failure(RuntimeException("not configured"))
    var notifyIsTypingResult: Result<Unit> = Result.failure(RuntimeException("not configured"))

    var lastChatId: ChatId? = null
    var lastMessageId: Long? = null
    var lastMessageIds: List<Long>? = null
    var lastAfterSequence: Long? = null
    var lastQueryOptions: QueryOptions? = null
    var lastContent: List<MessageContent>? = null
    var lastClientMessageId: ClientMessageId? = null
    var lastExpectedEventSequence: Long? = null
    var lastEmoji: Emoji? = null
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

    override fun getDelta(owner: Ed25519.KeyPair, chatId: ChatId, afterSequence: Long): Flow<Result<DeltaUpdate>> {
        lastChatId = chatId; lastAfterSequence = afterSequence
        return flowOf(Result.failure(RuntimeException("not configured")))
    }

    override suspend fun sendMessage(owner: Ed25519.KeyPair, chatId: ChatId, content: List<MessageContent>, clientMessageId: ClientMessageId): Result<ChatMessage> {
        lastChatId = chatId; lastContent = content; lastClientMessageId = clientMessageId
        return sendMessageResult
    }

    override suspend fun editMessage(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long, content: List<MessageContent>, expectedEventSequence: Long): Result<ChatMessage> {
        lastChatId = chatId; lastMessageId = messageId; lastContent = content; lastExpectedEventSequence = expectedEventSequence
        return editMessageResult
    }

    override suspend fun deleteMessage(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long, expectedEventSequence: Long): Result<ChatMessage> {
        lastChatId = chatId; lastMessageId = messageId; lastExpectedEventSequence = expectedEventSequence
        return deleteMessageResult
    }

    override suspend fun addReaction(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long, emoji: Emoji): Result<EmojiReaction> {
        lastChatId = chatId; lastMessageId = messageId; lastEmoji = emoji
        return addReactionResult
    }

    override suspend fun removeReaction(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long, emoji: Emoji): Result<EmojiReaction> {
        lastChatId = chatId; lastMessageId = messageId; lastEmoji = emoji
        return removeReactionResult
    }

    override suspend fun getReactors(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long, emoji: Emoji, queryOptions: QueryOptions): Result<ReactorsPage> {
        lastChatId = chatId; lastMessageId = messageId; lastEmoji = emoji; lastQueryOptions = queryOptions
        return getReactorsResult
    }

    override suspend fun getReactionSummary(owner: Ed25519.KeyPair, chatId: ChatId, messageId: Long): Result<ReactionSummary> {
        lastChatId = chatId; lastMessageId = messageId
        return getReactionSummaryResult
    }

    override suspend fun getReactionSummaries(owner: Ed25519.KeyPair, chatId: ChatId, queryOptions: QueryOptions): Result<List<ReactionSummary>> {
        lastChatId = chatId; lastQueryOptions = queryOptions
        return getReactionSummariesResult
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
