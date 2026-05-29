package com.getcode.util.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import androidx.core.graphics.scale

interface ContentReader {
    fun readBytes(uri: Uri): ByteArray?
    fun copyToCache(uri: Uri, fileName: String, maxSize: Int = Int.MAX_VALUE): Uri?
    fun removeFromCache(uri: Uri)
}

class AndroidContentReader(private val context: Context) : ContentReader {
    override fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            null
        }
    }

    override fun copyToCache(uri: Uri, fileName: String, maxSize: Int): Uri? {
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

        val file = File(context.cacheDir, fileName)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    override fun removeFromCache(uri: Uri) {
        uri.path?.let { File(it).delete() }
    }
}
