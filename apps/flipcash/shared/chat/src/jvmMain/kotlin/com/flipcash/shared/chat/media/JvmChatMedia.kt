package com.flipcash.shared.chat.media

import coil3.ImageLoader
import java.io.File

/** [ChatMediaLocation] for desktop: staged photos live under [root] in the app's own data directory. */
class DirectoryChatMediaLocation(root: File) : ChatMediaLocation {
    override val directory: File = File(root, ChatMediaUploads.DIRECTORY)
}

/** [SentImageCache] over the disk cache of [loader], the one `ChatPhotoFetcher` is registered on. */
class ImageLoaderSentImageCache(private val loader: () -> ImageLoader) : SentImageCache {
    override fun keep(key: String, file: File) {
        val cache = loader().diskCache ?: return
        val editor = cache.openEditor(key) ?: return
        try {
            cache.fileSystem.write(editor.data) { write(file.readBytes()) }
            editor.commit()
        } catch (t: Throwable) {
            editor.abort()
            throw t
        }
    }
}
