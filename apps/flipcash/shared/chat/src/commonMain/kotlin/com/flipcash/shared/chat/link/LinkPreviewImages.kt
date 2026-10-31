package com.flipcash.shared.chat.link

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import kotlin.time.Duration

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
