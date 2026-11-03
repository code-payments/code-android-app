package com.flipcash.shared.chat.link

import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.media.toMediaItem
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
