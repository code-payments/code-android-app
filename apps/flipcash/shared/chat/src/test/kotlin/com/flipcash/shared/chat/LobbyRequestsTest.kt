package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.EnterLobbyError
import com.flipcash.services.models.LeaveLobbyError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.Lobby
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class LobbyRequestsTest {

    private class MemoryStore : LobbyRequestStore {
        val entries = MutableStateFlow<Map<ChatId, Instant>>(emptyMap())
        override fun observe(): Flow<Map<ChatId, Instant>> = entries
        override suspend fun put(chatId: ChatId, enteredAt: Instant) { entries.value += chatId to enteredAt }
        override suspend fun remove(chatId: ChatId) { entries.value -= chatId }
    }

    private val chatController = mockk<ChatController>()
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val store = MemoryStore()
    private val requests = LobbyRequests(chatController, store, chatCoordinator)

    private val aa = ChatId("aa")
    private val bb = ChatId("bb")
    private val enteredAt = Instant.fromEpochSeconds(1_000)

    private fun group(chatId: ChatId, inLobby: Boolean) = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(0),
        title = "group",
        isPrivate = true,
        inLobby = inLobby,
    )

    @Test
    fun `a request that goes in is kept with the server's entered_at`() = runTest {
        coEvery { chatController.enterLobby(aa) } returns Result.success(Lobby(group(aa, true), enteredAt))

        assertEquals(enteredAt, requests.request(aa).getOrThrow())

        assertEquals(mapOf(aa to enteredAt), requests.observe().first())
    }

    @Test
    fun `a full lobby keeps nothing`() = runTest {
        coEvery { chatController.enterLobby(aa) } returns Result.failure(EnterLobbyError.LobbyFull())

        assertIs<EnterLobbyError.LobbyFull>(requests.request(aa).exceptionOrNull())

        assertEquals(emptyMap(), requests.observe().first())
        verify(exactly = 0) { chatCoordinator.refreshFeed() }
    }

    @Test
    fun `already a member drops the request and syncs the feed`() = runTest {
        store.put(aa, enteredAt)
        coEvery { chatController.enterLobby(aa) } returns Result.failure(EnterLobbyError.AlreadyMember())

        assertIs<EnterLobbyError.AlreadyMember>(requests.request(aa).exceptionOrNull())

        assertEquals(emptyMap(), requests.observe().first())
        verify { chatCoordinator.refreshFeed() }
    }

    @Test
    fun `cancel drops the request, and not found counts as cancelled`() = runTest {
        store.put(aa, enteredAt)
        store.put(bb, enteredAt)
        coEvery { chatController.leaveLobby(aa) } returns Result.success(Unit)
        coEvery { chatController.leaveLobby(bb) } returns Result.failure(LeaveLobbyError.NotFound())

        assertTrue(requests.cancel(aa).isSuccess)
        assertTrue(requests.cancel(bb).isSuccess)

        assertEquals(emptyMap(), requests.observe().first())
    }

    @Test
    fun `a failed cancel keeps the request`() = runTest {
        store.put(aa, enteredAt)
        coEvery { chatController.leaveLobby(aa) } returns Result.failure(LeaveLobbyError.Other())

        assertTrue(requests.cancel(aa).isFailure)

        assertEquals(mapOf(aa to enteredAt), requests.observe().first())
    }

    @Test
    fun `refresh drops requests no longer in the lobby and syncs the feed`() = runTest {
        store.put(aa, enteredAt)
        store.put(bb, enteredAt)
        coEvery { chatController.getChat(aa, any()) } returns Result.success(group(aa, inLobby = false))
        coEvery { chatController.getChat(bb, any()) } returns Result.success(group(bb, inLobby = true))

        requests.refresh()

        assertEquals(mapOf(bb to enteredAt), requests.observe().first())
        verify(exactly = 1) { chatCoordinator.refreshFeed() }
    }

    @Test
    fun `refresh keeps a request GetChat could not answer for`() = runTest {
        store.put(aa, enteredAt)
        coEvery { chatController.getChat(aa, any()) } returns Result.failure(Throwable("offline"))

        requests.refresh()

        assertEquals(mapOf(aa to enteredAt), requests.observe().first())
        verify(exactly = 0) { chatCoordinator.refreshFeed() }
    }

    @Test
    fun `refresh of one chat asks only about that chat, and only if it was requested`() = runTest {
        store.put(aa, enteredAt)
        coEvery { chatController.getChat(aa, any()) } returns Result.success(group(aa, inLobby = true))

        requests.refresh(listOf(aa, bb))

        assertEquals(mapOf(aa to enteredAt), requests.observe().first())
    }
}
