package com.flipcash.shared.chat

import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeInviteCipher
import com.flipcash.services.chat.GroupKeySource
import com.flipcash.services.chat.toInviteSecretEnvelope
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.CreateInviteError
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.shared.chat.HostInvites.Companion.isLostInviteAccess
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.InvitesPage
import com.flipcash.services.models.chat.ViewerState
import com.getcode.chatcipher.InviteCipher
import com.getcode.chatcipher.InviteLink
import com.getcode.ed25519kmp.KeyPair
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
    fun `a refused expiry that is refused again fails with the expiry error`() = runTest {
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } returns
            Result.failure(CreateInviteError.InvalidExpiresAt())

        val failed = assertIs<InviteCreation.Failed>(
            subject.create(chatId, InviteSettings(InviteExpiry.Never, InviteUses.NoLimit))
        )
        assertIs<CreateInviteError.InvalidExpiresAt>(failed.cause)
        coVerify(exactly = 2) { chatController.createInvite(chatId, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the shortest expiry has nothing shorter to retry at`() = runTest {
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } returns
            Result.failure(CreateInviteError.InvalidExpiresAt())

        assertIs<InviteCreation.Failed>(subject.create(chatId, InviteSettings(InviteExpiry.OneDay, InviteUses.NoLimit)))
        coVerify(exactly = 1) { chatController.createInvite(chatId, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an envelope that opens to another invite's secret gives no link`() = runTest {
        val real = invite(secret(1))
        // Sealed under this invite's id but holding a different secret, so it opens and then fails the id check.
        val id = real.id.bytes
        val forged = real.copy(
            secretEnvelope = cipher.sealInviteSecretEnvelope(ownKeys, secret(2), chatId.bytes, id)
                .toInviteSecretEnvelope(),
        )
        coEvery { chatController.getChatInvites(chatId, any()) } returns
            Result.success(InvitesPage(listOf(forged), null, false))

        val item = subject.load(chatId).getOrThrow().single()

        assertNull(item.url)
        assertFalse(item.canShare)
        assertNull(subject.current(chatId))
    }

    @Test
    fun `an envelope with an unsupported scheme gives no link`() = runTest {
        val real = invite(secret(1))
        val old = real.secretEnvelope!!
        val odd = real.copy(secretEnvelope = InviteSecretEnvelope(99, old.nonce, old.ciphertext))
        coEvery { chatController.getChatInvites(chatId, any()) } returns
            Result.success(InvitesPage(listOf(odd), null, false))

        val item = subject.load(chatId).getOrThrow().single()

        assertNull(item.url)
        assertFalse(item.canShare)
        assertNull(subject.current(chatId))
    }

    @Test
    fun `currentOrCreate reuses an existing link without calling createInvite`() = runTest {
        val mine = invite(secret(1))
        coEvery { chatController.getChatInvites(chatId, any()) } returns Result.success(InvitesPage(listOf(mine), null, false))
        subject.load(chatId)

        val result = assertIs<InviteCreation.Created>(subject.currentOrCreate(chatId))

        assertEquals(mine.id, result.invite.invite.id)
        coVerify(exactly = 0) { chatController.createInvite(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `two currentOrCreate calls at once make one link`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            Result.success(invite(cipher.last))
        }

        val both = (1..2).map { async { subject.currentOrCreate(chatId) } }
        runCurrent()
        gate.complete(Unit)
        val results = both.awaitAll()

        assertTrue(results.all { it is InviteCreation.Created })
        coVerify(exactly = 1) { chatController.createInvite(any(), any(), any(), any(), any(), any()) }
        assertEquals(1, subject.state.value[chatId]?.size)
    }

    @Test
    fun `concurrent creates run one after the other`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var inFlight = 0
        var peak = 0
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } coAnswers {
            inFlight++
            peak = maxOf(peak, inFlight)
            gate.await()
            inFlight--
            Result.success(invite(cipher.last))
        }

        val both = (1..2).map { async { subject.create(chatId, InviteSettings.Default) } }
        runCurrent()
        assertEquals(1, inFlight)
        gate.complete(Unit)
        both.awaitAll()

        assertEquals(1, peak)
        coVerify(exactly = 2) { chatController.createInvite(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the server returning the same invite twice adds one row`() = runTest {
        val same = invite(secret(5))
        coEvery { chatController.createInvite(chatId, any(), any(), any(), any(), any()) } returns Result.success(same)

        subject.create(chatId, InviteSettings.Default)
        subject.create(chatId, InviteSettings.Default)

        assertEquals(listOf(same.id), subject.state.value[chatId]?.map { it.invite.id })
    }

    @Test
    fun `clear between a failure and a retry makes a new secret`() = runTest {
        val tokens = mutableListOf<InviteToken>()
        coEvery { chatController.createInvite(chatId, capture(tokens), any(), any(), any(), any()) } returnsMany listOf(
            Result.failure(CreateInviteError.Other(IOException("dropped"))),
            Result.success(invite(secret(0))),
        )

        subject.create(chatId, InviteSettings.Default)
        subject.clear()
        subject.create(chatId, InviteSettings.Default)

        assertNotEquals(tokens[0], tokens[1])
    }

    @Test
    fun `a revoke failure leaves the invite as it was`() = runTest {
        val mine = invite(secret(1))
        coEvery { chatController.getChatInvites(chatId, any()) } returns Result.success(InvitesPage(listOf(mine), null, false))
        coEvery { chatController.revokeInvite(chatId, mine.id) } returns Result.failure(RevokeInviteError.Other(IOException()))
        subject.load(chatId)

        assertTrue(subject.revoke(chatId, mine.id).isFailure)

        assertEquals(Invite.Status.ACTIVE, subject.state.value[chatId]?.single()?.invite?.status)
        assertEquals(mine.id, subject.current(chatId)?.invite?.id)
    }

    @Test
    fun `a load failure keeps what was loaded before`() = runTest {
        val mine = invite(secret(1))
        coEvery { chatController.getChatInvites(chatId, any()) } returnsMany listOf(
            Result.success(InvitesPage(listOf(mine), null, false)),
            Result.failure(GetChatInvitesError.Other(IOException())),
        )
        subject.load(chatId)

        assertTrue(subject.load(chatId).isFailure)

        assertEquals(listOf(mine.id), subject.state.value[chatId]?.map { it.invite.id })
    }

    @Test
    fun `only a denied list or revoke means the viewer lost access`() {
        assertTrue(GetChatInvitesError.Denied().isLostInviteAccess())
        assertTrue(RevokeInviteError.Denied().isLostInviteAccess())
        assertFalse(CreateInviteError.Denied().isLostInviteAccess())
        assertFalse(GetChatInvitesError.NotFound().isLostInviteAccess())
        assertFalse(RevokeInviteError.NotFound().isLostInviteAccess())
        assertFalse(GetChatInvitesError.Other(IOException()).isLostInviteAccess())
        assertFalse(IOException().isLostInviteAccess())
    }

    @Test
    fun `a cancelled load throws instead of returning a failure`() = runTest {
        coEvery { chatController.getChatInvites(chatId, any()) } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { subject.load(chatId) }
        assertNull(subject.state.value[chatId])
    }

    @Test
    fun `a cancelled load result throws too`() = runTest {
        coEvery { chatController.getChatInvites(chatId, any()) } returns Result.failure(CancellationException("cancelled"))

        assertFailsWith<CancellationException> { subject.load(chatId) }
    }

    @Test
    fun `a cancelled create is not reported as a failure and keeps its secret`() = runTest {
        val tokens = mutableListOf<InviteToken>()
        coEvery { chatController.createInvite(chatId, capture(tokens), any(), any(), any(), any()) } throws
            CancellationException("cancelled") andThen Result.success(invite(secret(0)))

        assertFailsWith<CancellationException> { subject.create(chatId, InviteSettings.Default) }
        assertIs<InviteCreation.Created>(subject.create(chatId, InviteSettings.Default))

        assertEquals(tokens[0], tokens[1])
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
    fun `refreshAccess keeps the server's can_invite until a denial takes it away`() = runTest {
        coEvery { chatController.getChat(chatId, any()) } returns Result.success(
            ChatMetadata(
                chatId = chatId,
                type = ChatType.GROUP,
                members = emptyList(),
                lastMessage = null,
                lastActivity = now,
                viewerState = ViewerState(permissions = ViewerState.Permissions(canInvite = true)),
            ),
        )

        assertTrue(subject.refreshAccess(chatId).getOrThrow())
        assertEquals(true, subject.canInvite.value[chatId])

        subject.revokeAccess(chatId)
        assertEquals(false, subject.canInvite.value[chatId])
    }

    @Test
    fun `a failed refreshAccess leaves what was known`() = runTest {
        subject.revokeAccess(chatId)
        coEvery { chatController.getChat(chatId, any()) } returns Result.failure(IOException("offline"))

        assertTrue(subject.refreshAccess(chatId).isFailure)
        assertEquals(false, subject.canInvite.value[chatId])
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
