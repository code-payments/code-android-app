package com.flipcash.shared.chat.media

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.services.chat.BlobOpenFailure
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.OpenedBlob
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * What an image loader needs to draw a chat photo: the rendition to fetch and what it takes to open.
 *
 * The cache key is the blob id, never the signed URL, which rotates on every mint.
 *
 * @property sealed whether the message is end-to-end encrypted, so the bytes must be opened with
 * [senderId]'s key before they decode.
 * @property redacted a redacted message's photo is never fetched.
 */
data class ChatPhoto(
    val chatId: ChatId,
    val rendition: MediaItemRendition,
    val senderId: ID?,
    val sealed: Boolean,
    val redacted: Boolean = false,
) {
    val cacheKey: String get() = cacheKeyOf(rendition)

    companion object {
        fun cacheKeyOf(rendition: MediaItemRendition) = cacheKeyOf(rendition.blobId)
        fun cacheKeyOf(blobId: BlobId) = "chat-media-${blobId.bytes.toList().hexEncodedString()}"
    }
}

/** Why a photo can't be drawn; the UI shows "This photo can't be displayed" for every one. */
sealed class ChatPhotoUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Redacted : ChatPhotoUnavailable("Redacted message")

    /** No URL could be resolved for the blob. */
    class NotFound : ChatPhotoUnavailable("No download URL for the photo")

    /** The bytes could not be downloaded. */
    class Download(cause: Throwable?) : ChatPhotoUnavailable("Photo download failed", cause)

    /** The sealed bytes did not open or decode; [reason] says why. [BlobOpenFailure.KeyPending] can succeed later. */
    class Open(val reason: BlobOpenFailure) : ChatPhotoUnavailable("Photo did not open: $reason")
}

/**
 * The network-free parts of loading a chat photo, so the Coil fetcher around them stays thin:
 * URL resolution and opening sealed bytes.
 */
@Singleton
class ChatPhotoLoader internal constructor(
    private val blobStorage: BlobStorageController,
    private val crypto: ChatContentCrypto,
    private val userManager: UserManager,
    private val members: ChatMemberDataSource,
    private val now: () -> kotlin.time.Instant,
) {
    @Inject
    constructor(
        blobStorage: BlobStorageController,
        crypto: ChatContentCrypto,
        userManager: UserManager,
        members: ChatMemberDataSource,
    ) : this(blobStorage, crypto, userManager, members, { Clock.System.now() })

    /**
     * A URL to download [photo] from: the stored one while it is usable, else a freshly minted one.
     * [failedUrl] is one that just failed, which forces a mint even if it looks valid, since
     * metadata stored before expiry was modelled carries no expiry. Null if none can be had.
     */
    suspend fun resolveUrl(photo: ChatPhoto, failedUrl: String? = null): String? {
        if (photo.redacted) return null
        val stored = photo.rendition.blob?.downloadUrl?.takeIf { it.isNotBlank() }
        val usable = stored != null && stored != failedUrl && !photo.rendition.isDownloadUrlExpired(now())
        if (usable) return stored
        return blobStorage.refreshMetadata(listOf(photo.rendition.blobId), BlobAccessContext.Chat(photo.chatId))
            .getOrNull()
            ?.get(photo.rendition.cacheKey)
            ?.downloadUrl
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * The decodable bytes for [downloaded]: as they are for a plain photo, opened for a sealed one.
     * Throws [ChatPhotoUnavailable.Open] when they don't open.
     */
    suspend fun open(photo: ChatPhoto, downloaded: ByteArray): ByteArray {
        if (!photo.sealed) return downloaded
        val selfId = userManager.accountId ?: throw ChatPhotoUnavailable.Open(BlobOpenFailure.KeyPending)
        val peerId = members.getMembersForChat(photo.chatId).firstOrNull { it.userId != selfId }?.userId
            ?: throw ChatPhotoUnavailable.Open(BlobOpenFailure.KeyPending)
        val expected = photo.rendition.blob?.sizeBytes ?: throw ChatPhotoUnavailable.Open(BlobOpenFailure.Length)
        return when (
            val opened = crypto.openBlob(
                chatId = photo.chatId,
                selfId = selfId,
                peerId = peerId,
                senderId = photo.senderId,
                blobId = photo.rendition.blobId.bytes,
                expectedSize = expected,
                sealed = downloaded,
            )
        ) {
            is OpenedBlob.Plaintext -> opened.bytes
            is OpenedBlob.Failed -> throw ChatPhotoUnavailable.Open(opened.reason)
        }
    }
}
