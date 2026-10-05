package com.flipcash.services.chat

import com.codeinc.flipcash.gen.blob.v1.Model as BlobModel
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent

/**
 * What an encrypted message may carry for a photo, and nothing else. A message outside this is
 * shown as an update prompt rather than guessed at, which is the contract iOS holds.
 */
internal object SealedMediaContract {
    const val BLOB_ID_SIZE = 16
    const val MAX_MIME_LENGTH = 255
    const val MAX_BLURHASH_LENGTH = 64
    const val IMAGE_MIME_PREFIX = "image/"

    fun isValid(media: MessagingModel.MediaContent): Boolean {
        if (media.itemsCount != 1) return false
        val item = media.getItems(0)
        if (item.renditionsCount != 1) return false
        val rendition = item.getRenditions(0)
        if (rendition.role != BlobModel.Rendition.Role.ORIGINAL) return false
        if (!rendition.hasBlobId() || rendition.blobId.value.size() != BLOB_ID_SIZE) return false
        if (!rendition.hasBlob()) return false
        val blob = rendition.blob
        if (blob.mimeType.length !in 1..MAX_MIME_LENGTH) return false
        if (!blob.mimeType.startsWith(IMAGE_MIME_PREFIX, ignoreCase = true)) return false
        if (blob.sizeBytes < 1) return false
        if (blob.kindCase != BlobModel.BlobMetadata.KindCase.IMAGE) return false
        val image = blob.image
        return image.width >= 1 && image.height >= 1 && image.blurhash.length <= MAX_BLURHASH_LENGTH
    }
}

/** A [ChatContentCrypto.openBlob] failure. Each draws "This photo can't be displayed". */
sealed interface BlobOpenFailure {
    /** The cipher rejected the bytes: wrong key, wrong blob id, wrong sender, or tampering. */
    data object Authentication : BlobOpenFailure

    /** It opened, but to a length other than the `size_bytes` the message declared. */
    data object Length : BlobOpenFailure

    /** It opened to the right length but the image decoder couldn't read it. Raised by the caller that decodes. */
    data object Undecodable : BlobOpenFailure

    /** The chat key isn't available yet. Unlike the others, a later attempt can succeed. */
    data object KeyPending : BlobOpenFailure
}

/** Why a media send was refused before it reached the wire. */
class BlobSealingMismatchException(message: String) : IllegalStateException(message)

/**
 * Checks that an uploaded photo's sealing matches the chat it is about to be sent into.
 *
 * [sealedFor] is the chat the blob was sealed for (the `e2eeChat` it was uploaded with), or null
 * for a plain upload. [chatSeals] is whether the send will be encrypted. A plain blob in a
 * sealing chat would leak the photo past a promise the chat makes, and a sealed blob outside the
 * chat it was sealed for can't be opened by anyone who reads it.
 */
fun checkBlobSealing(sealedFor: ChatId?, sendChatId: ChatId, chatSeals: Boolean): Result<Unit> = when {
    sealedFor == null && chatSeals ->
        Result.failure(BlobSealingMismatchException("Plain blob into an encrypted chat"))
    sealedFor != null && !chatSeals ->
        Result.failure(BlobSealingMismatchException("Sealed blob into a chat that is not encrypted"))
    sealedFor != null && sealedFor != sendChatId ->
        Result.failure(BlobSealingMismatchException("Blob sealed for a different chat"))
    else -> Result.success(Unit)
}

/** Whether [this] carries a photo, directly or as the body of a reply. */
fun MessageContent.carriesMedia(): Boolean = when (this) {
    is MessageContent.Media -> true
    is MessageContent.Reply -> content.any { it is MessageContent.Media }
    else -> false
}

/** A copy with no download URL on any blob, which is what a sealed photo sends. */
internal fun MessageContent.withoutDownloadUrls(): MessageContent = when (this) {
    is MessageContent.Media -> copy(
        items = items.map { item ->
            item.copy(
                renditions = item.renditions.map { r ->
                    r.copy(blob = r.blob?.copy(downloadUrl = "", expiresAtMillis = null))
                },
            )
        },
    )
    is MessageContent.Reply -> copy(content = content.map { it.withoutDownloadUrls() })
    else -> this
}
