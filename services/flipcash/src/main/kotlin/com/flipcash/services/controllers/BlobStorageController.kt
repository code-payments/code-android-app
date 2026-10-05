package com.flipcash.services.controllers

import com.flipcash.services.BlobUploader
import com.flipcash.services.ForegroundGate
import com.flipcash.services.internal.extensions.withoutJpegMetadata
import com.flipcash.services.models.BlobNotReadyException
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.GetBlobsError
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.blob.UploadReservation
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
 *
 * Photos for a chat use [uploadChatMedia] or, when sealed for the chat, [uploadSealed].
 */
class BlobStorageController @Inject constructor(
    private val repository: BlobStorageRepository,
    private val uploader: BlobUploader,
    private val userManager: UserManager,
    private val foreground: ForegroundGate,
) {

    companion object {
        private val POLL_INTERVAL = 500.milliseconds
        private val POLL_TIMEOUT = 30.seconds

        // Chat media finalizes slower than an avatar (moderation, and for sealed bytes nothing to
        // transcode), and a user may background the app mid-send. So it polls on a poll budget
        // rather than wall time, and only while foregrounded.
        internal val CHAT_MEDIA_POLL_INTERVAL = 2.seconds
        internal const val CHAT_MEDIA_POLL_BUDGET = 30

        const val MIME_SEALED = "application/octet-stream"

        /** XChaCha20-Poly1305 framing on a sealed blob: 24-byte nonce plus 16-byte tag. */
        const val SEAL_OVERHEAD = 40
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
     *
     * [onProgress] reports `(sentBytes, totalBytes)` of the upload itself.
     */
    suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val owner = owner() ?: return noAccount()

        // Strip EXIF/GPS/XMP here, at the choke point every upload passes through, rather than
        // trusting each call site to have already re-encoded its bytes clean. The reservation
        // signs the byte count, so this must run before it — the size reserved has to match the
        // size actually uploaded.
        val bytes = bytes.withoutJpegMetadata()

        val reservation = repository.initiateExternalUpload(mimeType, bytes.size.toLong(), owner)
            .getOrElse { return Result.failure(it) }

        val stored = store(bytes, mimeType, reservation, owner, onProgress).getOrElse { return Result.failure(it) }
        return awaitReady(stored, owner)
    }

    /**
     * [upload] for a photo going into a chat: same handshake, but finalization polls the way chat
     * media needs (see [awaitChatMediaReady]). Plaintext, so the server can moderate it.
     */
    suspend fun uploadChatMedia(
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val stored = storeChatMedia(bytes, mimeType, onProgress).getOrElse { return Result.failure(it) }
        return awaitChatMediaReady(stored)
    }

    /**
     * The first half of [uploadChatMedia]: reserve, PUT/POST and signal completion, without waiting
     * for the server to finalize. Success means the bytes are in storage under the returned id;
     * [awaitChatMediaReady] finishes the job. A caller that must tell "never stored" from "stored
     * but not ready yet" — to re-upload in one case and only re-poll in the other — uses the pair.
     */
    suspend fun storeChatMedia(
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val owner = owner() ?: return noAccount()
        val bytes = bytes.withoutJpegMetadata()

        val reservation = repository.initiateExternalUpload(mimeType, bytes.size.toLong(), owner)
            .getOrElse { return Result.failure(it) }

        return store(bytes, mimeType, reservation, owner, onProgress)
    }

    /** Waits for a stored chat photo to finalize. See [awaitReadyChatMedia] for the polling rules. */
    suspend fun awaitChatMediaReady(blobId: BlobId): Result<BlobId> {
        val owner = owner() ?: return noAccount()
        return awaitReadyChatMedia(blobId, owner)
    }

    /**
     * Uploads [plaintext] end-to-end encrypted for [e2eeChat]. The bytes are reserved as an opaque
     * `application/octet-stream` of `plaintext + SEAL_OVERHEAD`, and [seal] is called with the
     * reserved blob id — the cipher binds the ciphertext to it, so it can't run before the
     * reservation. The cipher itself stays with the caller.
     *
     * [seal] must return exactly `plaintext.size + SEAL_OVERHEAD` bytes: the reserved size is
     * signed into the upload target, so anything else would be refused by storage after the fact.
     * The plaintext is not metadata-stripped here; the encoder already ships clean JPEGs.
     */
    suspend fun uploadSealed(
        plaintext: ByteArray,
        e2eeChat: ChatId,
        seal: suspend (blobId: BlobId) -> ByteArray,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val stored = storeSealed(plaintext, e2eeChat, seal, onProgress).getOrElse { return Result.failure(it) }
        return awaitChatMediaReady(stored)
    }

    /** The first half of [uploadSealed]; see [storeChatMedia] for why it is split from finalization. */
    suspend fun storeSealed(
        plaintext: ByteArray,
        e2eeChat: ChatId,
        seal: suspend (blobId: BlobId) -> ByteArray,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<BlobId> {
        val owner = owner() ?: return noAccount()
        val reservedSize = plaintext.size + SEAL_OVERHEAD

        val reservation = repository.initiateExternalUpload(
            mimeType = MIME_SEALED,
            sizeBytes = reservedSize.toLong(),
            owner = owner,
            e2eeChat = e2eeChat,
        ).getOrElse { return Result.failure(it) }

        val sealed = runCatching { seal(reservation.blobId) }
            .getOrElse { return Result.failure(InitiateExternalUploadError.Other(it)) }
        if (sealed.size != reservedSize) {
            return Result.failure(
                InitiateExternalUploadError.Other(
                    IllegalStateException("Sealed ${sealed.size} bytes but reserved $reservedSize")
                )
            )
        }

        return store(sealed, MIME_SEALED, reservation, owner, onProgress)
    }

    private suspend fun store(
        bytes: ByteArray,
        mimeType: String,
        reservation: UploadReservation,
        owner: Ed25519.KeyPair,
        onProgress: ((Long, Long) -> Unit)?,
    ): Result<BlobId> {
        uploader.upload(bytes, mimeType, reservation.target, onProgress)
            .getOrElse { return Result.failure(it) }

        // Advisory — the storage-completion event finalizes the blob even if this is skipped/fails.
        repository.completeExternalUpload(reservation.blobId, owner)

        return Result.success(reservation.blobId)
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

    /**
     * Chat-media finalization: [CHAT_MEDIA_POLL_BUDGET] polls [CHAT_MEDIA_POLL_INTERVAL] apart,
     * each one waiting for the app to be foregrounded first. The budget counts polls, not time, so
     * a backgrounded app doesn't spend it.
     *
     * A poll that fails in transit is swallowed and still counts — the next one may get through. A
     * denial is not transient and ends it. An id the server hasn't finished (or doesn't report a
     * status for) is processing.
     */
    private suspend fun awaitReadyChatMedia(blobId: BlobId, owner: Ed25519.KeyPair): Result<BlobId> {
        repeat(CHAT_MEDIA_POLL_BUDGET) { poll ->
            foreground.awaitForeground()

            val polled = repository.getBlobs(listOf(blobId), owner, BlobAccessContext.Owned)
            val denied = polled.exceptionOrNull() as? GetBlobsError.Denied
            if (denied != null) return Result.failure(denied)

            when (val blob = polled.getOrNull()?.firstOrNull()) {
                is BlobState.Ready -> return Result.success(blobId)
                is BlobState.Rejected -> return Result.failure(BlobRejectedException(blob.reason))
                null -> if (poll < CHAT_MEDIA_POLL_BUDGET - 1) delay(CHAT_MEDIA_POLL_INTERVAL)
            }
        }
        return Result.failure(BlobNotReadyException())
    }

    private fun owner(): Ed25519.KeyPair? = userManager.accountCluster?.authority?.keyPair

    private fun <T> noAccount(): Result<T> =
        Result.failure(Throwable("No account cluster in UserManager"))
}
