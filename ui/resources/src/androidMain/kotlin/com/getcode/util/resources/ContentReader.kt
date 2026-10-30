package com.getcode.util.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import androidx.core.graphics.scale

interface ContentReader {
    fun readBytes(uri: Uri): ByteArray?
    /** The content MIME type of [uri] (e.g. "image/jpeg"), or null if it can't be resolved. */
    fun mimeType(uri: Uri): String?
    /**
     * Re-encodes [uri] into the cache, downscaled to [maxSize] and stripped of EXIF/orientation
     * metadata. [mimeType] (the source type) selects the output format — JPEG is preserved,
     * everything else is written as PNG. Use [uploadMimeFor] to get the resulting type.
     */
    fun copyToCache(uri: Uri, fileName: String, maxSize: Int = Int.MAX_VALUE, mimeType: String? = null): Uri?
    fun removeFromCache(uri: Uri)
    /** The size of [uri]'s content in bytes, or null if it can't be resolved. */
    fun size(uri: Uri): Long?
}

private const val EXTENSION_JPG = "jpg"
private const val EXTENSION_JPEG = "jpeg"
private const val EXTENSION_PNG = "png"

/**
 * The MIME type the re-encoded [copyToCache] bytes will actually be for a given source type. Derived
 * via [MimeTypeMap] (no hardcoded MIME literals): a JPEG source stays JPEG, everything else is PNG.
 */
fun uploadMimeFor(sourceMimeType: String?): String {
    val extension = if (compressFormatFor(sourceMimeType) == Bitmap.CompressFormat.JPEG) {
        EXTENSION_JPEG
    } else {
        EXTENSION_PNG
    }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
}

private fun compressFormatFor(sourceMimeType: String?): Bitmap.CompressFormat {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(sourceMimeType?.lowercase())
    return if (extension == EXTENSION_JPG || extension == EXTENSION_JPEG) {
        Bitmap.CompressFormat.JPEG
    } else {
        Bitmap.CompressFormat.PNG
    }
}

class AndroidContentReader(private val context: Context) : ContentReader {
    override fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            null
        }
    }

    override fun mimeType(uri: Uri): String? {
        // ContentResolver knows content:// types; fall back to MimeTypeMap for file:// URIs.
        context.contentResolver.getType(uri)?.let { return it }
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }

    override fun copyToCache(uri: Uri, fileName: String, maxSize: Int, mimeType: String?): Uri? {
        val bytes = readBytes(uri) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val rotation = bytes.inputStream().use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }

        val oriented = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val scaled = if (oriented.width > maxSize || oriented.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(oriented.width, oriented.height)
            val newWidth = (oriented.width * scale).toInt()
            val newHeight = (oriented.height * scale).toInt()
            oriented.scale(newWidth, newHeight)
        } else {
            oriented
        }

        val format = compressFormatFor(mimeType)
        val quality = if (format == Bitmap.CompressFormat.JPEG) 90 else 100
        val file = File(context.cacheDir, fileName)
        file.outputStream().use { scaled.compress(format, quality, it) }
        return Uri.fromFile(file)
    }

    override fun removeFromCache(uri: Uri) {
        uri.path?.let { File(it).delete() }
    }

    override fun size(uri: Uri): Long? {
        // file:// (our cache) — measure the file directly; otherwise ask the resolver.
        if (uri.scheme == "file") {
            uri.path?.let { File(it).takeIf(File::exists)?.length()?.let { len -> return len } }
        }
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length.takeIf { it >= 0 }
            }
        } catch (_: java.io.FileNotFoundException) {
            null
        }
    }
}
