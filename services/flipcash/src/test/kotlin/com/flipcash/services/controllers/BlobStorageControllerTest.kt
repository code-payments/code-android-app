package com.flipcash.services.controllers

import com.flipcash.services.BlobUploader
import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.blob.UploadTarget
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.repository.BlobStorageRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BlobStorageControllerTest {

    private val repository = mockk<BlobStorageRepository>()
    private val uploader = mockk<BlobUploader>()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val controller = BlobStorageController(repository, uploader, userManager)

    private val blobId = BlobId(ByteArray(16) { 1 })
    private val target = UploadTarget(
        method = UploadTarget.Method.PUT,
        url = "https://storage.example/upload",
        headers = emptyMap(),
        formFields = emptyMap(),
        expiresAt = Instant.fromEpochSeconds(10_000),
    )

    private fun stubOwner() {
        val keyPair = mockk<Ed25519.KeyPair>(relaxed = true)
        val cluster = mockk<AccountCluster>(relaxed = true) {
            every { authority } returns mockk { every { this@mockk.keyPair } returns keyPair }
        }
        every { userManager.accountCluster } returns cluster
    }

    // The sealed model only represents terminal outcomes; non-terminal polls surface as an empty
    // getBlobs list (the service filters PENDING/PROCESSING out), so tests model those with emptyList().
    private fun readyBlob() = BlobState.Ready(id = blobId, metadata = mockk(relaxed = true))
    private fun rejectedBlob() = BlobState.Rejected(
        id = blobId,
        reason = BlobRejection(RejectionReason.UNKNOWN, ModerationResult.FlaggedCategory.NONE),
    )

    private fun happyPathStubs() {
        coEvery { repository.initiateExternalUpload(any(), any(), any()) } returns
            Result.success(UploadReservation(blobId, target))
        coEvery { uploader.upload(any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.completeExternalUpload(any(), any()) } returns Result.success(BlobStatus.PROCESSING)
    }

    @Test
    fun `upload fails when there is no account cluster`() = runTest {
        every { userManager.accountCluster } returns null

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.initiateExternalUpload(any(), any(), any()) }
    }

    @Test
    fun `upload returns the blob id once READY`() = runTest {
        stubOwner()
        happyPathStubs()
        coEvery { repository.getBlobs(any(), any()) } returns Result.success(listOf(readyBlob()))

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertEquals(blobId, result.getOrNull())
    }

    @Test
    fun `upload polls until the blob becomes READY`() = runTest {
        stubOwner()
        happyPathStubs()
        coEvery { repository.getBlobs(any(), any()) } returnsMany listOf(
            // Non-terminal polls resolve to an empty list; the controller keeps polling.
            Result.success(emptyList()),
            Result.success(emptyList()),
            Result.success(listOf(readyBlob())),
        )

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertEquals(blobId, result.getOrNull())
        coVerify(exactly = 3) { repository.getBlobs(any(), any()) }
    }

    @Test
    fun `upload fails with BlobRejectedException when the blob is REJECTED`() = runTest {
        stubOwner()
        happyPathStubs()
        coEvery { repository.getBlobs(any(), any()) } returns Result.success(listOf(rejectedBlob()))

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertIs<BlobRejectedException>(result.exceptionOrNull())
    }

    @Test
    fun `upload times out with BlobNotReadyException when never READY`() = runTest {
        stubOwner()
        happyPathStubs()
        // Never terminal — every poll resolves to an empty list until the timeout trips.
        coEvery { repository.getBlobs(any(), any()) } returns Result.success(emptyList())

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertIs<BlobNotReadyException>(result.exceptionOrNull())
    }

    @Test
    fun `upload fails and does not PUT when initiate fails`() = runTest {
        stubOwner()
        coEvery { repository.initiateExternalUpload(any(), any(), any()) } returns
            Result.failure(RuntimeException("initiate denied"))

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { uploader.upload(any(), any(), any()) }
    }

    @Test
    fun `upload fails and does not complete when the byte upload fails`() = runTest {
        stubOwner()
        coEvery { repository.initiateExternalUpload(any(), any(), any()) } returns
            Result.success(UploadReservation(blobId, target))
        coEvery { uploader.upload(any(), any(), any()) } returns Result.failure(RuntimeException("network"))

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.completeExternalUpload(any(), any()) }
    }

    @Test
    fun `upload still succeeds when the advisory complete call fails`() = runTest {
        stubOwner()
        coEvery { repository.initiateExternalUpload(any(), any(), any()) } returns
            Result.success(UploadReservation(blobId, target))
        coEvery { uploader.upload(any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.completeExternalUpload(any(), any()) } returns
            Result.failure(RuntimeException("complete failed"))
        coEvery { repository.getBlobs(any(), any()) } returns Result.success(listOf(readyBlob()))

        val result = controller.upload(byteArrayOf(1, 2, 3), "image/png")

        assertEquals(blobId, result.getOrNull())
    }

    @Test
    fun `getUploadPolicy fails without an account`() = runTest {
        every { userManager.accountCluster } returns null

        assertTrue(controller.getUploadPolicy().isFailure)
        coVerify(exactly = 0) { repository.getUploadPolicy(any()) }
    }
}
