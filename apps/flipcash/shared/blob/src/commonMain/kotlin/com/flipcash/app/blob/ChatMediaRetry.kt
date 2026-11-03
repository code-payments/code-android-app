package com.flipcash.app.blob

import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.CompleteExternalUploadError
import com.flipcash.services.models.GetBlobsError
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RejectionReason
import kotlin.time.Duration.Companion.seconds

/** Seals a photo for [chatId]; [seal] gets the reserved blob id, which the cipher binds to. */
class ChatMediaSealing(
    val chatId: ChatId,
    val seal: suspend (blobId: BlobId) -> ByteArray,
)

/** Which failed chat-photo stores are worth another attempt. */
object ChatMediaRetry {
    /** Waits before the first, second and third retry. */
    val BACKOFFS = listOf(1.seconds, 2.seconds, 4.seconds)

    /**
     * False for a refusal that repeating can't change: the server denied it, said the type or size
     * is wrong, or the user is out of quota; or moderation rejected the photo. Everything else —
     * transport errors, a non-2xx from storage, finalization that timed out or was rejected for
     * another reason, and failures nobody classified — is retried.
     */
    fun isRetryable(cause: Throwable): Boolean = when (cause) {
        is InitiateExternalUploadError.Denied,
        is InitiateExternalUploadError.UnsupportedType,
        is InitiateExternalUploadError.TooLarge,
        is InitiateExternalUploadError.QuotaExceeded,
        is CompleteExternalUploadError.NotFound,
        is CompleteExternalUploadError.NotUploaded,
        is GetBlobsError.Denied,
        is ChatMediaEncodingException -> false
        is BlobRejectedException -> cause.rejection.reason != RejectionReason.MODERATION
        else -> true
    }
}
