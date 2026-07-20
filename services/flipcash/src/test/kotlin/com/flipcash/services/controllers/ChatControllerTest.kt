package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.repository.ChatRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatControllerTest {

    private val repository = FakeChatRepository()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = ChatController(repository, userManager)

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    // region getChat

    @Test
    fun `getChat fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.getChat(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
    }

    @Test
    fun `getChat forwards the chatId to the repository`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        repository.getChatResult = Result.success(stubMetadata(chatId))

        controller.getChat(chatId)

        assertEquals(chatId, repository.lastChatId)
    }

    @Test
    fun `getChat returns the metadata from the repository`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        val expected = stubMetadata(chatId)
        repository.getChatResult = Result.success(expected)

        val result = controller.getChat(chatId)

        assertSame(expected, result.getOrThrow())
    }

    @Test
    fun `getChat surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("denied")
        repository.getChatResult = Result.failure(cause)

        val result = controller.getChat(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region getDmChatFeed

    @Test
    fun `getDmChatFeed fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.getDmChatFeed()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getDmChatFeed uses default QueryOptions when none provided`() = runTest {
        stubOwner()
        repository.getDmChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getDmChatFeed()

        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getDmChatFeed forwards custom query options`() = runTest {
        stubOwner()
        val token = listOf(0xAB.toByte())
        val options = QueryOptions(limit = 25, token = token, descending = false)
        repository.getDmChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getDmChatFeed(options)

        assertEquals(25, repository.lastQueryOptions?.limit)
        assertEquals(token, repository.lastQueryOptions?.token)
        assertEquals(false, repository.lastQueryOptions?.descending)
    }

    @Test
    fun `getDmChatFeed returns page with chats and paging state`() = runTest {
        stubOwner()
        val chat1 = stubMetadata(ChatId(ByteArray(32) { 1 }))
        val chat2 = stubMetadata(ChatId(ByteArray(32) { 2 }))
        val nextToken = listOf(0xFF.toByte())
        val page = ChatFeedPage(
            chats = listOf(chat1, chat2),
            pagingToken = nextToken,
            hasMore = true,
        )
        repository.getDmChatFeedResult = Result.success(page)

        val result = controller.getDmChatFeed()

        val returned = result.getOrThrow()
        assertEquals(2, returned.chats.size)
        assertEquals(nextToken, returned.pagingToken)
        assertTrue(returned.hasMore)
    }

    @Test
    fun `getDmChatFeed surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("server error")
        repository.getDmChatFeedResult = Result.failure(cause)

        val result = controller.getDmChatFeed()

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region helpers

    private fun stubMetadata(chatId: ChatId = ChatId(ByteArray(32))) = ChatMetadata(
        chatId = chatId,
        type = ChatType.CONTACT_DM,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1000),
    )

    // endregion
}

// region Fakes

private class FakeChatRepository : ChatRepository {
    var getChatResult: Result<ChatMetadata> = Result.failure(RuntimeException("not configured"))
    var getDmChatFeedResult: Result<ChatFeedPage> = Result.failure(RuntimeException("not configured"))
    var lastChatId: ChatId? = null
    var lastQueryOptions: QueryOptions? = null

    override suspend fun getChat(owner: Ed25519.KeyPair, chatId: ChatId): Result<ChatMetadata> {
        lastChatId = chatId
        return getChatResult
    }

    override suspend fun getDmChatFeed(owner: Ed25519.KeyPair, queryOptions: QueryOptions): Result<ChatFeedPage> {
        lastQueryOptions = queryOptions
        return getDmChatFeedResult
    }
}

// endregion
