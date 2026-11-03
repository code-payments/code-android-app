package com.flipcash.app.blob

import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * [UploadImageCache] for desktop: re-encodes with Skia into files under [directory]. The type of a
 * photo is read from its leading bytes, since a picked file's extension proves nothing.
 */
class JvmUploadImageCache(private val directory: File) : UploadImageCache {

    override fun mimeType(photo: PickedPhoto): String? {
        val head = photo.open()?.use { it.readNBytes(12) } ?: return null
        return when {
            head.startsWith(0xFF, 0xD8, 0xFF) -> MIME_JPEG
            head.startsWith(0x89, 0x50, 0x4E, 0x47) -> MIME_PNG
            head.startsWith('G'.code, 'I'.code, 'F'.code) -> "image/gif"
            head.size >= 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
                String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image/webp"
            else -> null
        }
    }

    override fun uploadMimeFor(sourceMimeType: String?): String =
        if (sourceMimeType?.lowercase() == MIME_JPEG) MIME_JPEG else MIME_PNG

    override fun copyToCache(photo: PickedPhoto, fileName: String, maxSize: Int, mimeType: String?): PickedPhoto? {
        val bytes = photo.readBytes() ?: return null
        val jpeg = uploadMimeFor(mimeType) == MIME_JPEG
        val image = renderSkiaImage(bytes, flatten = jpeg) { w, h ->
            val scale = if (maxOf(w, h) > maxSize) maxSize.toFloat() / maxOf(w, h) else 1f
            PixelSize(maxOf((w * scale).toInt(), 1), maxOf((h * scale).toInt(), 1))
        } ?: return null
        return image.use {
            val encoded = (if (jpeg) it.encodeToData(EncodedImageFormat.JPEG, 90) else it.encodeToData(EncodedImageFormat.PNG, 100))
                ?: return null
            val file = File(directory, fileName).also { f -> f.parentFile?.mkdirs() }
            file.writeBytes(encoded.bytes)
            FilePickedPhoto(file)
        }
    }

    override fun removeFromCache(photo: PickedPhoto) {
        (photo as? FilePickedPhoto)?.file?.delete()
    }

    override fun size(photo: PickedPhoto): Long? =
        (photo as? FilePickedPhoto)?.file?.takeIf { it.isFile }?.length()
            ?: photo.readBytes()?.size?.toLong()

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
    }
}

/** Reads a path, or a `file:` URI, as a [FilePickedPhoto]. */
class FilePickedPhotos : PickedPhotos {
    override fun fromLocation(location: String): PickedPhoto =
        FilePickedPhoto(if (location.startsWith("file:")) File(java.net.URI(location)) else File(location))
}
