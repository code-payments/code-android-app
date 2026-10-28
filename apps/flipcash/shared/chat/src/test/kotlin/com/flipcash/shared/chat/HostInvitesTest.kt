package com.flipcash.shared.chat

import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeInviteCipher
import com.flipcash.services.chat.GroupKeySource
import com.flipcash.services.chat.toInviteSecretEnvelope
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.CreateInviteError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.InvitesPage
import com.getcode.chatcipher.InviteCipher
import com.getcode.chatcipher.InviteLink
import com.getcode.ed25519kmp.KeyPair
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class HostInvitesTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val clock = object : Clock {
        override fun now() = now
    }
    private val chatId = ChatId(ByteArray(32) { 7 })
    private val self = List(32) { 1.toByte() }
    private val other = List(32) { 2.toByte() }
    private val ownKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val keys = object : ChatKeySource {
        override fun ownKeyPair() = ownKeys
        override fun ownUserId() = self
        override suspend fun peerPublicKey(userId: List<Byte>) = Result.success(ByteArray(32))
    }
    private val groupKeys = GroupKeySource { Result.success(ByteArray(32) { 9 }) }
    private val cipher = RecordingInviteCipher()
    private val chatController = mockk<ChatController>()
    private val subject = HostInvites(chatController, groupKeys, keys, cipher, clock)

    /** An invite as the server would list it, with a secret envelope this device can open. */
    private fun invite(
        secret: ByteArray,
        status: Invite.Status = Invite.Status.ACTIVE,
        createdAt: Instant = now,
        expiresAt: Instant? = null,
        createdBy: List<Byte> = self,
        openable: Boolean = true,
    ): Invite {
        val id = cipher.inviteId(secret)
        val envelope = cipher.sealInviteSecretEnvelope(
            if (openable) ownKeys else KeyPair(ByteArray(32) { 99 }, ByteArray(64)),
            secret,
            chatId.bytes,
            id,
        ).toInviteSecretEnvelope()
        return Invite(
            id = InviteId(id),
            chatId = chatId,
            createdBy = createdBy,
            maxUses = 0,
            useCount = 0,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = status,
            secretEnvelope = envelope,
        )
    }

    private fun secret(n: Int) = ByteArray(32) { (100 + n).toByte() }

    @Test
    fun `load pages to the end and keeps newest first`() = runTest {
        val older = invite(secret(1), createdAt = now - 2.days)
        val newer = invite(secret(2), createdAt = now - 1.days)
        coEvery { chatController.getChatInvites(chatId, QueryOptions()) } returns
            Result.success(InvitesPage(listOf(older), pagingToken = listOf(1), hasMore = true))
        coEvery { chatController.getChatInvites(chatId, QueryOptions(token = listOf(1))) } returns
            Result.success(InvitesPage(listOf(newer), pagingToken = null, hasMore = false))

        val list = subject.load(chatId).getOrThrow()

        assertEquals(listOf(newer.id, older.id), list.map { it.invite.id })
        assertEquals(InviteLink.url(secret(2)), list.first().url)
    }

    @Test
    fun `current is the newest active link the viewer made and can open`() = runTest {
        val unopenable = invite(secret(1), createdAt = now, openable = false)
        val someoneElses = invite(secret(2), createdAt = now - 1.days, createdBy = other)
        val revoked = invite(secret(3), createdAt = now - 2.days, status = Invite.Status.REVOKED)
        val mine = invite(secret(4), createdAt = now - 3.days)
        coEvery { chatController.getChatInvites(chatId, any()) } returns
            Result.success(InvitesPage(listOf(unopenable, someoneElses, revoked, mine), null, false))

        val list = subject.load(chatId).getOrThrow()

        assertFalse(list.first().canShare)
        assertEquals(mine.id, subject.current(chatId)?.invite?.id)
    }

    @Test
    fun `create seals both envelopes and sends the token of one fresh secret`() = runTest {
        val token = slot<InviteToken>()
        coEvery {
            chatController.createInvite(chatId, capture(token), any(), any(), 0, now + 7.days)
        } answers {
            Result.success(invite(cipher.last))
        }

        val result = subject.create(chatId, InviteSettings.Default)

        val created = assertIs<InviteCreation.Created>(result).invite
        assertEquals(InviteLink.url(cipher.last), created.url)
        assertTrue(token.captured.bytes.contentEquals(cipher.inviteToken(cipher.last)))
        assertEquals(created.invite.id, subject.current(chatId)?.invite?.id)
    }

    @Test
    fun `a transport failure keeps the secret so the retry sends the same token`() = runTest {
        val tokens = mutableListOf<InviteToken>()
        coEvery { chatController.createInvite(chatId, capture(tokens), any(), any(), any(), any()) } returnsMany listOf(
            Result.failure(CreateInviteError.Other(IOException("dropped"))),
            Result.success(invite(secret(0))),
        )

        assertIs<InviteCreation.Failed>(subject.create(chatId, InviteSettings.Default))
        subject.create(chatId, InviteSettings.Default)

        assertEquals(tokens[0], tokens[1])
    }

    @Test
    fun `an answered refusal drops the secret`() = runTest {
        val tokens = mutableListOf<InviteToken>()
        coEvery { chatController.createInvite(chatId, capture(tokens), any(), any(), any(), any()) } returns
            Result.failure(CreateInviteError.TooManyInvites())

        assertEquals(InviteCreation.TooMany, subject.create(chatId, InviteSettings.Default))
        subject.create(chatId, InviteSettings.Default)

        assertTrue(tokens[0] != tokens[1])
    }

    @Test
    fun `denied means not available and is not retried`() = runTest {
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } returns
            Result.failure(CreateInviteError.Denied())

        assertEquals(InviteCreation.NotAvailable, subject.create(chatId, InviteSettings.Default))
        coVerify(exactly = 1) { chatController.createInvite(chatId, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invalid signature fails without retry`() = runTest {
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } returns
            Result.failure(CreateInviteError.InvalidSignature())

        val failed = assertIs<InviteCreation.Failed>(subject.create(chatId, InviteSettings.Default))
        assertIs<CreateInviteError.InvalidSignature>(failed.cause)
        coVerify(exactly = 1) { chatController.createInvite(chatId, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an OK that is no longer active retries once with a new secret`() = runTest {
        val tokens = mutableListOf<InviteToken>()
        coEvery { chatController.createInvite(chatId, capture(tokens), any(), any(), any(), any()) } answers {
            val status = if (tokens.size == 1) Invite.Status.EXHAUSTED else Invite.Status.ACTIVE
            Result.success(invite(cipher.last, status = status))
        }

        assertIs<InviteCreation.Created>(subject.create(chatId, InviteSettings.Default))
        assertEquals(2, tokens.size)
        assertTrue(tokens[0] != tokens[1])
    }

    @Test
    fun `a refused expiry is retried once at the next shorter preset`() = runTest {
        val expiries = mutableListOf<Instant?>()
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), captureNullable(expiries)) } answers {
            if (expiries.size == 1) Result.failure(CreateInviteError.InvalidExpiresAt())
            else Result.success(invite(cipher.last))
        }

        subject.create(chatId, InviteSettings(InviteExpiry.Never, InviteUses.NoLimit))

        assertEquals(listOf(null, now + 30.days), expiries)
    }

    @Test
    fun `revoke marks the invite revoked so it is no longer current`() = runTest {
        val mine = invite(secret(1))
        coEvery { chatController.getChatInvites(chatId, any()) } returns Result.success(InvitesPage(listOf(mine), null, false))
        coEvery { chatController.revokeInvite(chatId, mine.id) } returns Result.success(Unit)
        subject.load(chatId)

        subject.revoke(chatId, mine.id).getOrThrow()

        assertNull(subject.current(chatId))
        assertEquals(Invite.Status.REVOKED, subject.state.value[chatId]?.single()?.invite?.status)
    }

    @Test
    fun `sections hide inactive links that ended more than 30 days ago`() {
        val active = HostInvite(invite(secret(1)), url = "a")
        val recentRevoke = HostInvite(invite(secret(2), status = Invite.Status.REVOKED, createdAt = now - 10.days), null)
        val oldRevoke = HostInvite(invite(secret(3), status = Invite.Status.REVOKED, createdAt = now - 31.days), null)
        val expiredRecently = HostInvite(
            invite(secret(4), status = Invite.Status.EXPIRED, createdAt = now - 60.days, expiresAt = now - 5.days),
            null,
        )

        val (shownActive, shownInactive) = HostInvites.sections(listOf(active, recentRevoke, oldRevoke, expiredRecently), now)

        assertEquals(listOf(active), shownActive)
        assertEquals(listOf(recentRevoke, expiredRecently), shownInactive)
    }

    @Test
    fun `a host invite never prints its link`() {
        val item = HostInvite(invite(secret(1)), InviteLink.url(secret(1)))
        assertFalse(item.toString().contains("#s="))
    }
}

/** Remembers the secret of the most recent create attempt. */
private class RecordingInviteCipher(
    private val fake: FakeInviteCipher = FakeInviteCipher(),
) : InviteCipher by fake {
    lateinit var last: ByteArray
    override fun newSecret(): ByteArray = fake.newSecret().also { last = it }
}
