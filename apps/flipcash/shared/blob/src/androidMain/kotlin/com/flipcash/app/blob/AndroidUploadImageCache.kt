package com.flipcash.app.blob

import android.content.Context
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.uploadMimeFor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [UploadImageCache] over [ContentReader], which already re-encodes into the app cache directory. */
internal class AndroidUploadImageCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contentReader: ContentReader,
) : UploadImageCache {
    override fun mimeType(photo: PickedPhoto): String? = contentReader.mimeType(photo.toUri())

    override fun uploadMimeFor(sourceMimeType: String?): String = uploadMimeFor(sourceMimeType)

    override fun copyToCache(photo: PickedPhoto, fileName: String, maxSize: Int, mimeType: String?): PickedPhoto? =
        contentReader.copyToCache(photo.toUri(), fileName, maxSize, mimeType)
            ?.let { UriPickedPhoto(it, context.contentResolver) }

    override fun removeFromCache(photo: PickedPhoto) = contentReader.removeFromCache(photo.toUri())

    override fun size(photo: PickedPhoto): Long? = contentReader.size(photo.toUri())
}
