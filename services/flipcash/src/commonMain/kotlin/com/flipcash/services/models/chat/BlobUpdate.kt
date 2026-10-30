package com.flipcash.services.models.chat

import com.flipcash.services.models.ModerationResult

// Real-time notification that blobs the user uploaded transitioned to a new
// lifecycle state (e.g. PROCESSING -> READY once the server finishes validating,
// transcoding, and moderating, or -> REJECTED on failure). Delivered over the
// event stream so clients react to upload completion instead of polling.
data class BlobUpdate(
    val blobs: List<BlobState>,
)

// A single blob's current status, plus its metadata once READY — or, once
// REJECTED, the reason it was rejected.
sealed class BlobState(
    val id: BlobId,
    val status: BlobStatus,
) {
    class Ready(
        id: BlobId,
        val metadata: BlobMetadata,
    ) : BlobState(id, BlobStatus.READY)
    class Rejected(
        id: BlobId,
        val reason: BlobRejection,
    ) : BlobState(id, BlobStatus.REJECTED)

}

enum class BlobStatus {
    UNKNOWN,
    PENDING,
    PROCESSING,
    READY,
    REJECTED,
}

// Explains why a blob was rejected during finalization. Rejection is terminal:
// the id will never become READY, so the client must upload again to retry.
data class BlobRejection(
    val reason: RejectionReason,
    // Best-fit moderation category. Meaningful only when reason == MODERATION.
    val flaggedCategory: ModerationResult.FlaggedCategory,
)

enum class RejectionReason {
    UNKNOWN,
    MODERATION,
    UNSUPPORTED_TYPE,
    MISMATCHED_TYPE,
    TOO_LARGE,
    CORRUPT,
    INTERNAL,
    PRIVACY_METADATA,
}
