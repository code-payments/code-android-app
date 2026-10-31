package com.flipcash.shared.chat.media

import java.io.File

/** Where staged chat photos are written: the app's own files, so they survive the chat screen and a restart. */
interface ChatMediaLocation {
    /** The directory the encoded `<id>.jpg` files go in. */
    val directory: File
}

/**
 * Hands a just-uploaded photo to the image loader's disk cache under [key], where [ChatPhotoFetcher]
 * looks first, so the sent message draws without downloading what was just uploaded. The loader is
 * the platform's (it needs a context to find), hence the seam.
 */
fun interface SentImageCache {
    fun keep(key: String, file: File)
}
