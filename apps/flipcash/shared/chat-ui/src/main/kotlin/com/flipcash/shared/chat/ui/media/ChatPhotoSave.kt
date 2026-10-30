package com.flipcash.shared.chat.ui.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import com.flipcash.shared.chat.media.ChatPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ALBUM = "Flipcash"

/**
 * Returns a function that saves the photo [model] draws to the device's gallery and then reports
 * whether it worked through [onResult]. The image is read back through Coil, which has just loaded
 * it for the viewer and so answers from its cache. A [ChatPhoto] saves the cached original as it
 * is when it's a JPEG, PNG or WebP; anything else is re-encoded as a JPEG.
 *
 * Needs no permission: the app's minSdk is 29, where MediaStore lets an app add its own pictures.
 */
@Composable
fun rememberSavePhoto(model: Any?, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentModel by rememberUpdatedState(model)
    val currentOnResult by rememberUpdatedState(onResult)
    return save@{
        val data = currentModel ?: return@save
        scope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    val imageLoader = SingletonImageLoader.get(context)
                    val result = imageLoader.execute(ImageRequest.Builder(context).data(data).size(Size.ORIGINAL).build())
                    if (result !is SuccessResult) return@withContext false
                    // The fetcher caches the opened original under the photo's key, so the file the
                    // sender uploaded is on disk by now; saving it avoids a lossy re-encode.
                    val original = (data as? ChatPhoto)?.let { photo ->
                        imageLoader.diskCache?.openSnapshot(photo.cacheKey)?.use { snapshot ->
                            imageLoader.diskCache?.fileSystem?.read(snapshot.data) { readByteArray() }
                        }
                    }
                    val type = original?.let(::sniffImageType)
                    if (original != null && type != null) {
                        saveBytesToGallery(context, original, type)
                    } else {
                        val bitmap = result.image.toBitmap()
                        saveToGallery(context, bitmap)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            currentOnResult(saved)
        }
    }
}

/** An image type [saveBytesToGallery] can write as it is: its MIME type and file extension. */
internal data class ImageType(val mime: String, val extension: String)

/** Reads the type from [bytes]' signature; null when it isn't one the gallery takes as it is. */
internal fun sniffImageType(bytes: ByteArray): ImageType? {
    fun at(offset: Int, vararg values: Int) =
        bytes.size >= offset + values.size && values.indices.all { (bytes[offset + it].toInt() and 0xFF) == values[it] }
    return when {
        at(0, 0xFF, 0xD8, 0xFF) -> ImageType("image/jpeg", "jpg")
        at(0, 0x89, 0x50, 0x4E, 0x47) -> ImageType("image/png", "png")
        at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> ImageType("image/webp", "webp")
        else -> null
    }
}

/** [saveToGallery] for bytes that are already an encoded image: written untouched. */
internal fun saveBytesToGallery(context: Context, bytes: ByteArray, type: ImageType): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "flipcash_${System.currentTimeMillis()}.${type.extension}")
        put(MediaStore.Images.Media.MIME_TYPE, type.mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (_: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

/**
 * Inserts [bitmap] into the shared pictures collection as a JPEG. `IS_PENDING` keeps the file out of
 * other apps' queries until it is fully written; a failed write removes the half-made row.
 */
internal fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "flipcash_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
        check(written)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (_: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}
