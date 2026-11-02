package com.flipcash.app.blob

import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.blob.UploadPolicy

/**
 * What the server will accept for one chat photo upload, resolved from the cached
 * [UploadPolicy]. A bound of 0 means unbounded.
 *
 * [maxBytes] is a cap on the *encoded JPEG*, already net of any framing the upload adds, so the
 * encoder compares against it directly.
 */
data class ChatMediaLimits(
    val maxBytes: Long,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxPixels: Long,
) {
    companion object {
        /**
         * Limits for a photo sealed for a chat: the policy's encrypted image bounds, and its size
         * ceiling less [BlobStorageController.SEAL_OVERHEAD] — the ceiling applies to the sealed
         * bytes, the encoder measures the plaintext.
         */
        fun sealed(policy: UploadPolicy): Result<ChatMediaLimits> {
            val encrypted = policy.encrypted
                ?: return Result.failure(ChatMediaEncodingException.EncryptionNotAllowed())
            return Result.success(
                ChatMediaLimits(
                    maxBytes = encrypted.maxSizeBytes - BlobStorageController.SEAL_OVERHEAD,
                    maxWidth = encrypted.image?.maxWidth ?: 0,
                    maxHeight = encrypted.image?.maxHeight ?: 0,
                    maxPixels = encrypted.image?.maxPixels ?: 0,
                )
            )
        }

        /** The entry the policy names for [ChatMediaEncoder.UPLOAD_MIME_TYPE], in policy order. */
        fun plain(policy: UploadPolicy): Result<ChatMediaLimits> {
            val index = ChatMediaConstraints.firstMatchIndex(
                patterns = policy.mimeTypeConstraints.map { it.mimeTypePattern },
                mimeType = ChatMediaEncoder.UPLOAD_MIME_TYPE,
            ) ?: return Result.failure(ChatMediaEncodingException.NoMatchingConstraint())

            val constraint = policy.mimeTypeConstraints[index]
            return Result.success(
                ChatMediaLimits(
                    maxBytes = constraint.maxSizeBytes,
                    maxWidth = constraint.image?.maxWidth ?: 0,
                    maxHeight = constraint.image?.maxHeight ?: 0,
                    maxPixels = constraint.image?.maxPixels ?: 0,
                )
            )
        }
    }
}

/** Why a photo could not be turned into an uploadable JPEG. None of these are worth retrying. */
sealed class ChatMediaEncodingException(message: String) : Exception(message) {
    /** The policy has no entry for `image/jpeg`. */
    class NoMatchingConstraint : ChatMediaEncodingException("Policy has no entry for image/jpeg")

    /** The policy carries no end-to-end-encrypted constraints, so a sealed upload isn't allowed. */
    class EncryptionNotAllowed : ChatMediaEncodingException("Policy does not allow encrypted uploads")

    /** Even the lowest-quality encode is over the byte cap. */
    class TooLarge : ChatMediaEncodingException("Photo is too large at every quality")

    /** The source couldn't be decoded, or no quality produced bytes. */
    class EncodingFailed(cause: Throwable? = null) : ChatMediaEncodingException("Photo could not be encoded") {
        init {
            cause?.let { initCause(it) }
        }
    }
}
