package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.models.UserProfile
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

        val result = controller.getDmChatFeed(ChatType.CONTACT_DM)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getDmChatFeed uses default QueryOptions when none provided`() = runTest {
        stubOwner()
        repository.getDmChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getDmChatFeed(ChatType.CONTACT_DM)

        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getDmChatFeed forwards custom query options`() = runTest {
        stubOwner()
        val token = listOf(0xAB.toByte())
        val options = QueryOptions(limit = 25, token = token, descending = false)
        repository.getDmChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getDmChatFeed(ChatType.CONTACT_DM, options)

        assertEquals(25, repository.lastQueryOptions?.limit)
        assertEquals(token, repository.lastQueryOptions?.token)
        assertEquals(false, repository.lastQueryOptions?.descending)
    }

    @Test
    fun `getDmChatFeed forwards chat type filter`() = runTest {
        stubOwner()
        repository.getDmChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getDmChatFeed(ChatType.TIP_DM)

        assertEquals(ChatType.TIP_DM, repository.lastChatType)
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

        val result = controller.getDmChatFeed(ChatType.CONTACT_DM)

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

        val result = controller.getDmChatFeed(ChatType.CONTACT_DM)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region getGroupChatFeed

    @Test
    fun `getGroupChatFeed fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.getGroupChatFeed()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getGroupChatFeed uses default QueryOptions when none provided`() = runTest {
        stubOwner()
        repository.getGroupChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getGroupChatFeed()

        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getGroupChatFeed forwards the paging token`() = runTest {
        stubOwner()
        val token = listOf(0xAB.toByte())
        repository.getGroupChatFeedResult = Result.success(ChatFeedPage(emptyList(), null, false))

        controller.getGroupChatFeed(QueryOptions(limit = 25, token = token))

        assertEquals(25, repository.lastQueryOptions?.limit)
        assertEquals(token, repository.lastQueryOptions?.token)
    }

    @Test
    fun `getGroupChatFeed returns page with chats and paging state`() = runTest {
        stubOwner()
        val nextToken = listOf(0xFF.toByte())
        repository.getGroupChatFeedResult = Result.success(
            ChatFeedPage(
                chats = listOf(stubMetadata(ChatId(ByteArray(32) { 1 }))),
                pagingToken = nextToken,
                hasMore = true,
            )
        )

        val returned = controller.getGroupChatFeed().getOrThrow()

        assertEquals(1, returned.chats.size)
        assertEquals(nextToken, returned.pagingToken)
        assertTrue(returned.hasMore)
    }

    @Test
    fun `getGroupChatFeed surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("server error")
        repository.getGroupChatFeedResult = Result.failure(cause)

        val result = controller.getGroupChatFeed()

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region getRoster

    @Test
    fun `getRoster fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.getRoster(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
    }

    @Test
    fun `getRoster uses default QueryOptions when none provided`() = runTest {
        stubOwner()
        repository.getRosterResult = Result.success(stubRosterPage())

        controller.getRoster(ChatId(ByteArray(32)))

        assertEquals(QueryOptions(), repository.lastQueryOptions)
    }

    @Test
    fun `getRoster forwards the chatId and paging token`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        val token = listOf(0xAB.toByte())
        repository.getRosterResult = Result.success(stubRosterPage())

        controller.getRoster(chatId, QueryOptions(token = token))

        assertEquals(chatId, repository.lastChatId)
        assertEquals(token, repository.lastQueryOptions?.token)
    }

    @Test
    fun `getRoster returns the page from the repository`() = runTest {
        stubOwner()
        val expected = stubRosterPage()
        repository.getRosterResult = Result.success(expected)

        val result = controller.getRoster(ChatId(ByteArray(32)))

        assertSame(expected, result.getOrThrow())
    }

    @Test
    fun `getRoster surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("not found")
        repository.getRosterResult = Result.failure(cause)

        val result = controller.getRoster(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region editChat

    @Test
    fun `editChat fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.editChat(ChatId(ByteArray(32)), EditChatParameters())

        assertTrue(result.isFailure)
    }

    @Test
    fun `editChat forwards the chatId and parameters to the repository`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        val parameters = EditChatParameters(title = "New title")
        repository.editChatResult = Result.success(stubMetadata(chatId))

        controller.editChat(chatId, parameters)

        assertEquals(chatId, repository.lastChatId)
        assertSame(parameters, repository.lastEditChatParameters)
    }

    @Test
    fun `editChat returns the updated metadata from the repository`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        val expected = stubMetadata(chatId)
        repository.editChatResult = Result.success(expected)

        val result = controller.editChat(chatId, EditChatParameters())

        assertSame(expected, result.getOrThrow())
    }

    @Test
    fun `editChat surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("title moderated")
        repository.editChatResult = Result.failure(cause)

        val result = controller.editChat(ChatId(ByteArray(32)), EditChatParameters())

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region joinChat

    @Test
    fun `joinChat fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.joinChat(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
    }

    @Test
    fun `joinChat returns the joined chat's metadata`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x42 })
        val expected = stubMetadata(chatId)
        repository.joinChatResult = Result.success(expected)

        val result = controller.joinChat(chatId)

        assertEquals(chatId, repository.lastChatId)
        assertSame(expected, result.getOrThrow())
    }

    @Test
    fun `joinChat surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("rules not satisfied")
        repository.joinChatResult = Result.failure(cause)

        val result = controller.joinChat(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
    }

    // endregion

    // region leaveChat

    @Test
    fun `leaveChat fails when no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.leaveChat(ChatId(ByteArray(32)))

        assertTrue(result.isFailure)
    }

    @Test
    fun `leaveChat forwards the chatId to the repository`() = runTest {
        stubOwner()
        val chatId = ChatId(ByteArray(32) { 0x7 })
        repository.leaveChatResult = Result.success(Unit)

        val result = controller.leaveChat(chatId)

        assertEquals(chatId, repository.lastChatId)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `leaveChat surfaces repository failures without swallowing`() = runTest {
        stubOwner()
        val cause = RuntimeException("denied")
        repository.leaveChatResult = Result.failure(cause)

        val result = controller.leaveChat(ChatId(ByteArray(32)))

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

    private fun stubRosterPage() = RosterPage(
        members = listOf(
            ChatMember(
                userId = listOf(1.toByte()),
                userProfile = UserProfile("Member", emptyList(), null, null),
                pointers = emptyList(),
            )
        ),
        rosterSummary = RosterSummary(memberCount = 1, version = 1),
        pagingToken = null,
        hasMore = false,
    )

    // endregion
}

// region Fakes

private class FakeChatRepository : ChatRepository {
    var getChatResult: Result<ChatMetadata> = Result.failure(RuntimeException("not configured"))
    var getDmChatFeedResult: Result<ChatFeedPage> = Result.failure(RuntimeException("not configured"))
    var getGroupChatFeedResult: Result<ChatFeedPage> = Result.failure(RuntimeException("not configured"))
    var startChatResult: Result<ChatMetadata> = Result.failure(RuntimeException("not configured"))
    var joinChatResult: Result<ChatMetadata> = Result.failure(RuntimeException("not configured"))
    var leaveChatResult: Result<Unit> = Result.failure(RuntimeException("not configured"))
    var muteChatResult: Result<ViewerState> = Result.failure(RuntimeException("not configured"))
    var unmuteChatResult: Result<ViewerState> = Result.failure(RuntimeException("not configured"))
    var getRosterResult: Result<RosterPage> = Result.failure(RuntimeException("not configured"))
    var editChatResult: Result<ChatMetadata> = Result.failure(RuntimeException("not configured"))
    var lastChatId: ChatId? = null
    var lastQueryOptions: QueryOptions? = null
    var lastChatType: ChatType? = null
    var lastMuteState: MuteState? = null
    var lastEditChatParameters: EditChatParameters? = null

    override suspend fun getChat(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        viewMode: ViewMode,
    ): Result<ChatMetadata> {
        lastChatId = chatId
        return getChatResult
    }

    override suspend fun getDmChatFeed(
        owner: Ed25519.KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<ChatFeedPage> {
        lastQueryOptions = queryOptions
        lastChatType = chatType
        return getDmChatFeedResult
    }

    override suspend fun getGroupChatFeed(
        owner: Ed25519.KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage> {
        lastQueryOptions = queryOptions
        return getGroupChatFeedResult
    }

    override suspend fun startChat(
        owner: Ed25519.KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata> {
        return startChatResult
    }

    override suspend fun getRoster(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<RosterPage> {
        lastChatId = chatId
        lastQueryOptions = queryOptions
        return getRosterResult
    }

    override suspend fun editChat(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata> {
        lastChatId = chatId
        lastEditChatParameters = parameters
        return editChatResult
    }

    override suspend fun joinChat(owner: Ed25519.KeyPair, chatId: ChatId): Result<ChatMetadata> {
        lastChatId = chatId
        return joinChatResult
    }

    override suspend fun leaveChat(owner: Ed25519.KeyPair, chatId: ChatId): Result<Unit> {
        lastChatId = chatId
        return leaveChatResult
    }

    override suspend fun muteChat(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): Result<ViewerState> {
        lastChatId = chatId
        lastMuteState = mute
        return muteChatResult
    }

    override suspend fun unmuteChat(owner: Ed25519.KeyPair, chatId: ChatId): Result<ViewerState> {
        lastChatId = chatId
        return unmuteChatResult
    }
}

// endregion
