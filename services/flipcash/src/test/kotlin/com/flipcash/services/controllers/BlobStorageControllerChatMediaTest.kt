package com.flipcash.services.controllers

import com.flipcash.services.BlobUploader
import com.flipcash.services.ForegroundGate
import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.GetBlobsError
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.blob.UploadTarget
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.repository.BlobStorageRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BlobStorageControllerChatMediaTest {

    private val repository = mockk<BlobStorageRepository>()
    private val uploader = mockk<BlobUploader>()
    private val userManager = mockk<UserManager>(relaxed = true)

    private val blobId = BlobId(ByteArray(16) { 7 })
    private val chatId = ChatId(ByteArray(32) { 3 })
    private val target = UploadTarget(
        method = UploadTarget.Method.PUT,
        url = "https://storage.example/upload",
        headers = emptyMap(),
        formFields = emptyMap(),
        expiresAt = Instant.fromEpochSeconds(10_000),
    )

    private fun controller(foreground: ForegroundGate = ForegroundGate { }) =
        BlobStorageController(repository, uploader, userManager, foreground)

    private fun stubHappyUpload() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
        coEvery { repository.initiateExternalUpload(any(), any(), any(), any()) } returns
            Result.success(UploadReservation(blobId, target))
        coEvery { uploader.upload(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.completeExternalUpload(any(), any()) } returns Result.success(BlobStatus.PROCESSING)
    }

    private fun ready() = BlobState.Ready(id = blobId, metadata = mockk(relaxed = true))
    private fun rejected(reason: RejectionReason) = BlobState.Rejected(
        id = blobId,
        reason = BlobRejection(reason, ModerationResult.FlaggedCategory.NONE),
    )

    // MARK: - Sealed upload -

    @Test
    fun `sealed upload reserves an opaque blob sized plaintext plus framing for the chat`() = runTest {
        stubHappyUpload()
        val mime = slot<String>()
        val size = slot<Long>()
        val chat = slot<ChatId?>()
        coEvery { repository.initiateExternalUpload(capture(mime), capture(size), any(), captureNullable(chat)) } returns
            Result.success(UploadReservation(blobId, target))
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.success(listOf(ready()))
        val plaintext = ByteArray(100) { 1 }
        val sealed = ByteArray(140) { 9 }
        var sealedWith: BlobId? = null

        val result = controller().uploadSealed(plaintext, chatId, seal = { id -> sealedWith = id; sealed })

        assertEquals(blobId, result.getOrNull())
        assertEquals("application/octet-stream", mime.captured)
        assertEquals(140L, size.captured)
        assertEquals(chatId, chat.captured)
        assertEquals(blobId, sealedWith)
        coVerify { uploader.upload(match { it.contentEquals(sealed) }, "application/octet-stream", target, any()) }
    }

    @Test
    fun `sealed upload fails without uploading when the sealed size differs from the reservation`() = runTest {
        stubHappyUpload()

        val result = controller().uploadSealed(ByteArray(100), chatId, seal = { ByteArray(139) })

        assertIs<InitiateExternalUploadError.Other>(result.exceptionOrNull())
        coVerify(exactly = 0) { uploader.upload(any(), any(), any(), any()) }
    }

    @Test
    fun `sealed upload fails without uploading when sealing throws`() = runTest {
        stubHappyUpload()

        val result = controller().uploadSealed(ByteArray(100), chatId, seal = { error("no key") })

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { uploader.upload(any(), any(), any(), any()) }
    }

    @Test
    fun `plain chat upload sends no chat and declares the given type`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.success(listOf(ready()))

        controller().uploadChatMedia(byteArrayOf(1, 2, 3), "image/jpeg")

        coVerify { repository.initiateExternalUpload("image/jpeg", 3L, any(), null) }
    }

    // MARK: - Progress -

    @Test
    fun `progress reported by the uploader reaches the caller`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.success(listOf(ready()))
        coEvery { uploader.upload(any(), any(), any(), any()) } coAnswers {
            arg<((Long, Long) -> Unit)?>(3)?.invoke(50L, 100L)
            Result.success(Unit)
        }
        val seen = mutableListOf<Pair<Long, Long>>()

        controller().upload(byteArrayOf(1), "image/png") { sent, total -> seen += sent to total }

        assertEquals(listOf(50L to 100L), seen)
    }

    // MARK: - Chat media finalization -

    @Test
    fun `chat media polls every two seconds until ready, as the owner`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returnsMany listOf(
            Result.success(emptyList()),
            Result.success(emptyList()),
            Result.success(listOf(ready())),
        )

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        assertEquals(blobId, result.getOrNull())
        assertEquals(4_000L, testScheduler.currentTime)
        coVerify(exactly = 3) { repository.getBlobs(listOf(blobId), any(), BlobAccessContext.Owned) }
    }

    @Test
    fun `chat media gives up after thirty polls`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.success(emptyList())

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        assertIs<BlobNotReadyException>(result.exceptionOrNull())
        coVerify(exactly = 30) { repository.getBlobs(any(), any(), any()) }
        // Thirty polls, twenty-nine waits: no sleep after the last one.
        assertEquals(58_000L, testScheduler.currentTime)
    }

    @Test
    fun `a failed poll is swallowed and still spends the budget`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returnsMany listOf(
            Result.failure(GetBlobsError.Other(RuntimeException("offline"))),
            Result.failure(GetBlobsError.Other(RuntimeException("offline"))),
            Result.success(listOf(ready())),
        )

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        assertEquals(blobId, result.getOrNull())
        coVerify(exactly = 3) { repository.getBlobs(any(), any(), any()) }
    }

    @Test
    fun `only failed polls exhaust the budget as timed out`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns
            Result.failure(GetBlobsError.Other(RuntimeException("offline")))

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        assertIs<BlobNotReadyException>(result.exceptionOrNull())
        coVerify(exactly = 30) { repository.getBlobs(any(), any(), any()) }
    }

    @Test
    fun `a rejection ends polling and carries its reason`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns
            Result.success(listOf(rejected(RejectionReason.MODERATION)))

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        val failure = assertIs<BlobRejectedException>(result.exceptionOrNull())
        assertEquals(RejectionReason.MODERATION, failure.rejection.reason)
        coVerify(exactly = 1) { repository.getBlobs(any(), any(), any()) }
    }

    @Test
    fun `a denied poll ends polling`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.failure(GetBlobsError.Denied())

        val result = controller().uploadChatMedia(byteArrayOf(1), "image/jpeg")

        assertIs<GetBlobsError.Denied>(result.exceptionOrNull())
        coVerify(exactly = 1) { repository.getBlobs(any(), any(), any()) }
    }

    @Test
    fun `polling waits for the foreground and checks it before every poll`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returnsMany listOf(
            Result.success(emptyList()),
            Result.success(listOf(ready())),
        )
        val foregrounded = CompletableDeferred<Unit>()
        var checks = 0
        val gate = ForegroundGate { checks++; foregrounded.await() }

        val upload = async { controller(gate).uploadChatMedia(byteArrayOf(1), "image/jpeg") }
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getBlobs(any(), any(), any()) }

        foregrounded.complete(Unit)
        advanceUntilIdle()

        assertEquals(blobId, upload.await().getOrNull())
        assertEquals(2, checks)
    }

    @Test
    fun `the profile path keeps polling without the foreground gate`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returns Result.success(listOf(ready()))
        var checks = 0

        controller(ForegroundGate { checks++ }).upload(byteArrayOf(1), "image/png")

        assertEquals(0, checks)
    }

    // MARK: - Store and finalize, split -

    @Test
    fun `storing returns the blob id without polling for finalization`() = runTest {
        stubHappyUpload()

        val result = controller().storeChatMedia(byteArrayOf(1), "image/jpeg")

        assertEquals(blobId, result.getOrNull())
        coVerify(exactly = 0) { repository.getBlobs(any(), any(), any()) }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `a store that fails in transit reports the failure and never finalizes`() = runTest {
        stubHappyUpload()
        coEvery { uploader.upload(any(), any(), any(), any()) } returns Result.failure(RuntimeException("offline"))

        val result = controller().storeSealed(ByteArray(10), chatId, seal = { ByteArray(50) })

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.completeExternalUpload(any(), any()) }
        coVerify(exactly = 0) { repository.getBlobs(any(), any(), any()) }
    }

    @Test
    fun `awaiting a stored blob polls it as the owner until ready`() = runTest {
        stubHappyUpload()
        coEvery { repository.getBlobs(any(), any(), any()) } returnsMany listOf(
            Result.success(emptyList()),
            Result.success(listOf(ready())),
        )

        val result = controller().awaitChatMediaReady(blobId)

        assertEquals(blobId, result.getOrNull())
        coVerify(exactly = 0) { repository.initiateExternalUpload(any(), any(), any(), any()) }
        coVerify(exactly = 2) { repository.getBlobs(listOf(blobId), any(), BlobAccessContext.Owned) }
    }
}
