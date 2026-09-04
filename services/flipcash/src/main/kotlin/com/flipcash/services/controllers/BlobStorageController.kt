package com.flipcash.services.controllers

import com.flipcash.services.BlobUploader
import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.repository.BlobStorageRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.utils.base58
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

    /**
     * Re-resolves [blobIds] to freshly minted [BlobMetadata] — the recovery `DownloadUrl.expires_at`
     * calls for. A blob's bytes are immutable but its `download_url` is per-fetch and expiring, so
     * any metadata that has been held a while (persisted profile pictures, chat media) needs this
     * before its URL can be fetched again.
     *
     * Returns only the ids that came back READY, keyed by base58 blob id — ids still processing or
     * rejected are simply absent, leaving the caller's existing metadata in place.
     *
     * [context] names the surface the blobs are being read from. It is what authorizes ids the
     * caller does not own — another user's avatar resolves only through
     * [BlobAccessContext.Profile], chat media only through [BlobAccessContext.Chat] — and without
     * it the server omits them from the response rather than failing, so a wrong context looks
     * exactly like a blob that isn't ready yet.
     */
    suspend fun refreshMetadata(
        blobIds: List<BlobId>,
        context: BlobAccessContext,
    ): Result<Map<String, BlobMetadata>> {
        if (blobIds.isEmpty()) return Result.success(emptyMap())
        val owner = owner() ?: return noAccount()
        return repository.getBlobs(blobIds, owner, context).map { blobs ->
            blobs.filterIsInstance<BlobState.Ready>()
                .associate { it.id.bytes.base58 to it.metadata }
        }
    }

    private suspend fun awaitReady(blobId: BlobId, owner: Ed25519.KeyPair): Result<BlobId> {
        var elapsed: Duration = Duration.ZERO
        while (elapsed < POLL_TIMEOUT) {
            // A single-id query resolves to at most one blob, so first() is the one we asked for.
            val blob = repository.getBlobs(listOf(blobId), owner, BlobAccessContext.Owned)
                .getOrElse { return Result.failure(it) }
                .firstOrNull()

            when (blob) {
                is BlobState.Ready -> return Result.success(blobId)
                is BlobState.Rejected -> return Result.failure(BlobRejectedException(blob.reason))
                // null — still pending/processing (non-terminal states resolve to null); keep polling.
                null -> {
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
