package com.flipcash.shared.chat.ui.media

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Returns a function that shares the photo [model] draws. The image is read back through Coil, which
 * has just loaded it for the viewer and so answers from its cache, then written under
 * [chatMediaShareDir] where the share sheet is allowed to read it.
 */
@Composable
fun rememberSharePhoto(model: Any?): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentModel by rememberUpdatedState(model)
    return share@{
        val data = currentModel ?: return@share
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    val result = SingletonImageLoader.get(context)
                        .execute(ImageRequest.Builder(context).data(data).build())
                    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                    File(chatMediaShareDir(context), "photo_${System.currentTimeMillis()}.jpg").also { target ->
                        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (file != null) shareImage(context, file)
        }
    }
}
