package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

/**
 * The surface a caller is reading blobs from, used to authorize `GetBlobs`.
 *
 * Blobs the caller owns resolve on their own, but an id the caller does *not* own is treated as
 * unauthorized and silently omitted from the response unless a context that grants it is supplied.
 * Re-minting another user's avatar therefore fails closed — an empty response, not an error — so
 * every re-mint of someone else's media has to name the surface it is being read from.
 */
sealed interface BlobAccessContext {
    /** The caller owns these blobs (their own profile picture, their own uploads). */
    data object Owned : BlobAccessContext

    /**
     * Read from [userId]'s public profile. Grants only the renditions of that user's *current*
     * profile picture — a superseded picture stops resolving through it.
     */
    data class Profile(val userId: ID) : BlobAccessContext

    /** Read from within [chatId]. Granted iff the caller is a member and the blob was shared into it. */
    data class Chat(val chatId: ChatId) : BlobAccessContext

    companion object {
        /**
         * [Profile] for [userId], falling back to [Owned] when the id isn't known. The fallback is
         * for surfaces that hold a picture without the profile it belongs to; it resolves the
         * caller's own blobs and quietly resolves nothing for anyone else's, which is the same
         * outcome as passing no context at all.
         */
        fun profile(userId: ID?): BlobAccessContext = userId?.let(::Profile) ?: Owned
    }
}
