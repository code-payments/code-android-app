package com.flipcash.shared.chat.link

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.media.toMediaItem
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A preview image ready to send, and the chat its bytes were sealed for (null if they went up plain). */
class SettledPreviewImage(val item: MediaItem, val sealedFor: ChatId?)

interface LinkPreviewImages {
    /**
     * Waits for chip [id] to upload and returns it as a preview image, or null if it failed, was
     * removed or is still going after the deadline. The chip is removed either way.
     */
    suspend fun settle(id: String): SettledPreviewImage?

    /** Drops chip [id] unsent, for a preview that won't go out. */
    fun discard(id: String)

    object None : LinkPreviewImages {
        override suspend fun settle(id: String): SettledPreviewImage? = null
        override fun discard(id: String) = Unit
    }
}

internal class UploadedLinkPreviewImages(
    private val uploads: ChatMediaUploads,
    private val deadline: Duration,
) : LinkPreviewImages {
    @Inject constructor(uploads: ChatMediaUploads) : this(uploads, DEADLINE)

    override suspend fun settle(id: String): SettledPreviewImage? {
        val settled = withTimeoutOrNull(deadline) { uploads.awaitSettled(id) }
        val photo = (settled as? ChatMediaUploadState.Uploaded)?.photo
        // Kept under the blob's cache key, so the sent bubble draws the image without fetching it back.
        uploads.remove(id, keepAs = photo?.let { ChatPhoto.cacheKeyOf(it.blobId) })
        return photo?.let { SettledPreviewImage(it.toMediaItem(), it.sealedFor) }
    }

    override fun discard(id: String) = uploads.remove(id)

    private companion object {
        // The text is already on screen as sending; past this it goes without the image.
        val DEADLINE = 20.seconds
    }
}
