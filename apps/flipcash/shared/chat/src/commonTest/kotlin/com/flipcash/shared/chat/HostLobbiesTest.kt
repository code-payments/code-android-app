package com.flipcash.shared.chat

import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.AdmitLobbyMemberError
import com.flipcash.services.models.DenyLobbyMemberError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.services.models.chat.LobbyPage
import com.flipcash.services.models.chat.LobbyUpdate
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class HostLobbiesTest {

    private val chatController = mockk<ChatController>()
    private val keyEnvelopes = mockk<GroupKeyEnvelopes>()
    private val lobbies = HostLobbies(chatController, keyEnvelopes)

    private val chat = ChatId("aa")
    private val envelope = KeyEnvelope(scheme = 1, nonce = ByteArray(12), ciphertext = ByteArray(48))

    private fun member(id: Byte, enteredAt: Long) = LobbyMember(
        userProfile = UserProfile(
            displayName = "user $id",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            userId = listOf(id),
        ),
        publicKey = PublicKey("11111111111111111111111111111111"),
        enteredAt = Instant.fromEpochSeconds(enteredAt),
    )

    private val older = member(1, 100)
    private val newer = member(2, 200)

    private fun ids() = lobbies.state.value[chat].orEmpty().map { it.userProfile.userId }

    @Test
    fun `load reads every page and orders newest first`() = runTest {
        val token: PagingToken = listOf(7)
        coEvery { chatController.getLobbyMembers(chat, match { it.token == null }) } returns
            Result.success(LobbyPage(listOf(older), token, hasMore = true))
        coEvery { chatController.getLobbyMembers(chat, match { it.token == token }) } returns
            Result.success(LobbyPage(listOf(newer), null, hasMore = false))

        lobbies.load(chat).getOrThrow()

        assertEquals(listOf(listOf<Byte>(2), listOf<Byte>(1)), ids())
    }

    @Test
    fun `a failed load keeps what was held`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older)))
        coEvery { chatController.getLobbyMembers(chat, any()) } returns Result.failure(IllegalStateException())

        assertTrue(lobbies.load(chat).isFailure)

        assertEquals(listOf(listOf<Byte>(1)), ids())
    }

    @Test
    fun `stream updates add once and remove by user`() {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older), LobbyUpdate.MemberEntered(newer)))
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(newer)))
        assertEquals(listOf(listOf<Byte>(2), listOf<Byte>(1)), ids())

        lobbies.apply(chat, listOf(LobbyUpdate.MemberLeft(listOf(2))))
        assertEquals(listOf(listOf<Byte>(1)), ids())
    }

    @Test
    fun `approve wraps the key for the member and admits them`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older)))
        coEvery { keyEnvelopes.wrapFor(chat, any()) } returns Result.success(envelope)
        coEvery { chatController.admitLobbyMember(chat, listOf(1), envelope) } returns Result.success(Unit)

        lobbies.approve(chat, older).getOrThrow()

        coVerify { chatController.admitLobbyMember(chat, listOf(1), envelope) }
        assertEquals(emptyList(), ids())
    }

    @Test
    fun `approving someone who already left drops them without an error`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older)))
        coEvery { keyEnvelopes.wrapFor(chat, any()) } returns Result.success(envelope)
        coEvery { chatController.admitLobbyMember(chat, any(), any()) } returns
            Result.failure(AdmitLobbyMemberError.NotInLobby())

        assertTrue(lobbies.approve(chat, older).isSuccess)
        assertEquals(emptyList(), ids())
    }

    @Test
    fun `a key that cannot be wrapped admits no one and keeps the row`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older)))
        coEvery { keyEnvelopes.wrapFor(chat, any()) } returns Result.failure(IllegalStateException())

        assertTrue(lobbies.approve(chat, older).isFailure)

        coVerify(exactly = 0) { chatController.admitLobbyMember(any(), any(), any()) }
        assertEquals(listOf(listOf<Byte>(1)), ids())
    }

    @Test
    fun `deny removes the row, and one already gone counts as done`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older), LobbyUpdate.MemberEntered(newer)))
        coEvery { chatController.denyLobbyMember(chat, listOf(1)) } returns Result.success(Unit)
        coEvery { chatController.denyLobbyMember(chat, listOf(2)) } returns
            Result.failure(DenyLobbyMemberError.NotFound())

        assertTrue(lobbies.deny(chat, listOf(1)).isSuccess)
        assertTrue(lobbies.deny(chat, listOf(2)).isSuccess)

        assertEquals(emptyList(), ids())
    }

    @Test
    fun `a failed deny keeps the row`() = runTest {
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older)))
        coEvery { chatController.denyLobbyMember(chat, any()) } returns
            Result.failure(DenyLobbyMemberError.Denied())

        assertTrue(lobbies.deny(chat, listOf(1)).isFailure)
        assertEquals(listOf(listOf<Byte>(1)), ids())
    }

    @Test
    fun `reload refetches the lobbies held and no others`() = runTest {
        val other = ChatId("bb")
        lobbies.apply(chat, listOf(LobbyUpdate.MemberEntered(older), LobbyUpdate.MemberEntered(newer)))
        // The server has since admitted `newer`; the stream that would have said so was down.
        coEvery { chatController.getLobbyMembers(chat, any()) } returns
            Result.success(LobbyPage(listOf(older), null, hasMore = false))

        lobbies.reload()

        assertEquals(listOf(listOf<Byte>(1)), ids())
        coVerify(exactly = 0) { chatController.getLobbyMembers(other, any()) }
    }
}
