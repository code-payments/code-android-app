package com.flipcash.services.controllers

import com.flipcash.services.BlobUploader
import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.repository.BlobStorageRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Client for blob-storage uploads. [upload] performs the entire direct-to-storage handshake behind a
 * single call — reserve, PUT/POST the bytes, advise completion, and poll until the blob is READY —
 * returning the durable [BlobId] to hand to e.g. `ProfileController.setProfilePicture`. Callers use
 * [getUploadPolicy] up front to filter selection and validate size before uploading.
 */
class BlobStorageController @Inject constructor(
    private val repository: BlobStorageRepository,
    private val uploader: BlobUploader,
    private val userManager: UserManager,
) {

    private companion object {
        val POLL_INTERVAL = 500.milliseconds
        val POLL_TIMEOUT = 30.seconds
    }

    /** The server's current upload constraints (accepted MIME types + size ceilings). */
    suspend fun getUploadPolicy(): Result<UploadPolicy> {
        val owner = owner() ?: return noAccount()
        return repository.getUploadPolicy(owner)
    }

    /**
     * Uploads [bytes] to storage and returns the READY [BlobId]. Reserves a presigned target,
     * PUTs/POSTs the bytes directly to storage, signals completion, and polls until the server
     * finishes validating/transcoding — so callers never orchestrate the individual steps.
     */
    suspend fun upload(bytes: ByteArray, mimeType: String): Result<BlobId> {
        val owner = owner() ?: return noAccount()

        val reservation = repository.initiateExternalUpload(mimeType, bytes.size.toLong(), owner)
            .getOrElse { return Result.failure(it) }

        uploader.upload(bytes, mimeType, reservation.target)
            .getOrElse { return Result.failure(it) }

        // Advisory — the storage-completion event finalizes the blob even if this is skipped/fails.
        repository.completeExternalUpload(reservation.blobId, owner)

        return awaitReady(reservation.blobId, owner)
    }

    private suspend fun awaitReady(blobId: BlobId, owner: Ed25519.KeyPair): Result<BlobId> {
        var elapsed: Duration = Duration.ZERO
        while (elapsed < POLL_TIMEOUT) {
            // A single-id query resolves to at most one blob, so first() is the one we asked for.
            val blob = repository.getBlobs(listOf(blobId), owner)
                .getOrElse { return Result.failure(it) }
                .firstOrNull()

            when (blob?.status) {
                BlobStatus.READY -> return Result.success(blobId)
                BlobStatus.REJECTED -> return Result.failure(BlobRejectedException(blob.rejection))
                else -> {
                    delay(POLL_INTERVAL)
                    elapsed += POLL_INTERVAL
                }
            }
        }
        return Result.failure(BlobNotReadyException())
    }

    private fun owner(): Ed25519.KeyPair? = userManager.accountCluster?.authority?.keyPair

    private fun <T> noAccount(): Result<T> =
        Result.failure(Throwable("No account cluster in UserManager"))
}
