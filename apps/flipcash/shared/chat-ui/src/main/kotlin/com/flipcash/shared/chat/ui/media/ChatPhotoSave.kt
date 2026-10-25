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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ALBUM = "Flipcash"

/**
 * Returns a function that saves the photo [model] draws to the device's gallery and then reports
 * whether it worked through [onResult]. The image is read back through Coil, which has just loaded
 * it for the viewer and so answers from its cache.
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
                    val result = SingletonImageLoader.get(context)
                        .execute(ImageRequest.Builder(context).data(data).size(Size.ORIGINAL).build())
                    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext false
                    saveToGallery(context, bitmap)
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
