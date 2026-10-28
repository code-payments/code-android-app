package com.flipcash.shared.chat.invite

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeInviteCipher
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetInviteError
import com.flipcash.services.models.GetRosterError
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InvitePreview
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.ed25519kmp.KeyPair
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** The joiner's calls: the token is derived from the secret, and every server answer maps to one state. */
class GroupInviteLandingTest {

    private val cipher = FakeInviteCipher()
    private val keys = mockk<ChatKeySource> { every { ownKeyPair() } returns KeyPair(ByteArray(32), ByteArray(64)) }
    private val controller = mockk<ChatController>()
    private val metadata = mockk<ChatMetadataDataSource>()
    private val coordinator = mockk<ChatCoordinator>()
    private val landing = GroupInviteLanding(cipher, keys, controller, metadata, coordinator)

    private val secret = ByteArray(32) { 3 }
    private val chatId = ChatId(ByteArray(16) { 4 }.toList())
    private val token = InviteToken(cipher.inviteToken(secret))

    private val chat = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(1),
        title = "Moony",
        rosterSummary = RosterSummary(memberCount = 48, version = 1),
    )

    private fun preview(status: Invite.Status) = InvitePreview(chat, status)

    @Test
    fun `lookup asks with the token derived from the secret`() = runTest {
        coEvery { controller.getInvite(token) } returns Result.success(preview(Invite.Status.ACTIVE))
        coEvery { metadata.isMember(chatId) } returns false
        val result = landing.lookup(secret)
        assertEquals(InviteLookup.Found(token, chat, null), result)
    }

    @Test
    fun `a member is sent to the chat`() = runTest {
        coEvery { controller.getInvite(token) } returns Result.success(preview(Invite.Status.ACTIVE))
        coEvery { metadata.isMember(chatId) } returns true
        assertEquals(InviteLookup.AlreadyMember(chatId), landing.lookup(secret))
    }

    @Test
    fun `each dead status carries its reason`() = runTest {
        coEvery { metadata.isMember(chatId) } returns false
        val expected = mapOf(
            Invite.Status.EXHAUSTED to DeadInviteReason.EXHAUSTED,
            Invite.Status.EXPIRED to DeadInviteReason.EXPIRED,
            Invite.Status.REVOKED to DeadInviteReason.REVOKED,
        )
        expected.forEach { (status, reason) ->
            coEvery { controller.getInvite(token) } returns Result.success(preview(status))
            assertEquals(reason, (landing.lookup(secret) as InviteLookup.Found).dead)
        }
    }

    @Test
    fun `lookup errors map to their frames`() = runTest {
        coEvery { controller.getInvite(token) } returns Result.failure(GetInviteError.NotFound())
        assertEquals(InviteLookup.NotFound, landing.lookup(secret))
        coEvery { controller.getInvite(token) } returns Result.failure(GetInviteError.Banned())
        assertEquals(InviteLookup.Banned, landing.lookup(secret))
        coEvery { controller.getInvite(token) } returns Result.failure(GetInviteError.Other(RuntimeException("offline")))
        assertEquals(InviteLookup.Failed, landing.lookup(secret))
    }

    @Test
    fun `a roster refusal is Denied and any other failure is Failed`() = runTest {
        coEvery { controller.getRoster(chatId, any(), token) } returns Result.failure(GetRosterError.Denied())
        assertEquals(InviteRosterResult.Denied, landing.roster(chatId, token, null))
        coEvery { controller.getRoster(chatId, any(), token) } returns Result.failure(GetRosterError.Other())
        assertEquals(InviteRosterResult.Failed, landing.roster(chatId, token, null))
    }

    @Test
    fun `a roster page carries the next token only while more remain`() = runTest {
        val member = mockk<ChatMember>(relaxed = true)
        val summary = RosterSummary(memberCount = 48, version = 1)
        coEvery { controller.getRoster(chatId, any(), token) } returns
            Result.success(RosterPage(listOf(member), summary, listOf<Byte>(1), hasMore = true))
        assertEquals(listOf<Byte>(1), (landing.roster(chatId, token, null) as InviteRosterResult.Page).next)
        coEvery { controller.getRoster(chatId, any(), token) } returns
            Result.success(RosterPage(listOf(member), summary, listOf<Byte>(1), hasMore = false))
        assertEquals(null, (landing.roster(chatId, token, null) as InviteRosterResult.Page).next)
    }

    @Test
    fun `join sends the token and a sealed secret`() = runTest {
        coEvery { coordinator.joinWithInvite(chatId, any()) } returns Result.success(Unit)
        assertEquals(InviteJoinResult.Joined(chatId), landing.join(secret, chatId, token))
        coVerify { coordinator.joinWithInvite(chatId, match { it.token == token }) }
    }

    @Test
    fun `join errors map to their outcomes`() = runTest {
        val cases = listOf(
            JoinChatError.InviteExhausted() to InviteJoinResult.Dead(DeadInviteReason.EXHAUSTED),
            JoinChatError.InviteExpired() to InviteJoinResult.Dead(DeadInviteReason.EXPIRED),
            JoinChatError.InviteRevoked() to InviteJoinResult.Dead(DeadInviteReason.REVOKED),
            JoinChatError.InviteNotFound() to InviteJoinResult.NotFound,
            JoinChatError.NotFound() to InviteJoinResult.GroupGone,
            JoinChatError.Banned() to InviteJoinResult.Banned,
            JoinChatError.Denied() to InviteJoinResult.Refused,
            JoinChatError.RulesNotSatisfied() to InviteJoinResult.Refused,
            JoinChatError.Other(RuntimeException("offline")) to InviteJoinResult.Failed,
        )
        cases.forEach { (error, outcome) ->
            coEvery { coordinator.joinWithInvite(chatId, any()) } returns Result.failure(error)
            assertEquals(outcome, landing.join(secret, chatId, token), error::class.simpleName)
        }
    }

    @Test
    fun `join without an account fails without calling the server`() = runTest {
        every { keys.ownKeyPair() } returns null
        assertIs<InviteJoinResult.Failed>(landing.join(secret, chatId, token))
        coVerify(exactly = 0) { coordinator.joinWithInvite(any(), any()) }
    }
}
