package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.utils.ErrorUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Which JoinChat failures reach error reporting. */
class InternalChatRepositoryJoinTest {

    private val service = mockk<ChatService>()
    private val repository = InternalChatRepository(service, mockk(), mockk())
    private val owner = mockk<KeyPair>()
    private val chatId = ChatId(ByteArray(16) { 5 }.toList())

    @Before
    fun setUp() {
        mockkObject(ErrorUtils)
        every { ErrorUtils.handleError(any()) } returns Unit
    }

    @After
    fun tearDown() = unmockkObject(ErrorUtils)

    private suspend fun joinFailing(error: JoinChatError) {
        coEvery { service.joinChat(owner, chatId, any()) } returns Result.failure(error)
        repository.joinChat(owner, chatId, invite = null)
    }

    @Test
    fun `a dead invite is not reported`() = runTest {
        joinFailing(JoinChatError.InviteExpired())
        verify(exactly = 0) { ErrorUtils.handleError(any()) }
    }

    @Test
    fun `an unknown invite is not reported`() = runTest {
        joinFailing(JoinChatError.InviteNotFound())
        verify(exactly = 0) { ErrorUtils.handleError(any()) }
    }

    @Test
    fun `a ban is not reported`() = runTest {
        joinFailing(JoinChatError.Banned())
        verify(exactly = 0) { ErrorUtils.handleError(any()) }
    }

    @Test
    fun `a vanished group is not reported`() = runTest {
        joinFailing(JoinChatError.NotFound())
        verify(exactly = 0) { ErrorUtils.handleError(any()) }
    }

    @Test
    fun `denied is reported`() = runTest {
        val denied = JoinChatError.Denied()
        joinFailing(denied)
        verify(exactly = 1) { ErrorUtils.handleError(denied) }
    }
}
