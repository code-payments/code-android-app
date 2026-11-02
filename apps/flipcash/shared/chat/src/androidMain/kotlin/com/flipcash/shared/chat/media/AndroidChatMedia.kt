package com.flipcash.shared.chat.media

import android.content.Context
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** [ChatMediaLocation] under `Context.filesDir`. */
internal class FilesDirChatMediaLocation @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ChatMediaLocation {
    override val directory: File get() = File(context.filesDir, ChatMediaUploads.DIRECTORY)
}

/** [SentImageCache] over the singleton Coil loader's disk cache. */
internal class CoilSentImageCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SentImageCache {
    override fun keep(key: String, file: File) {
        val cache = SingletonImageLoader.get(context).diskCache ?: return
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
