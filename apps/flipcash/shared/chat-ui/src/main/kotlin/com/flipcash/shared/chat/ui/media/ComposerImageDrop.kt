package com.flipcash.shared.chat.ui.media

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Takes the images in [candidates] that the composer will stage, in order.
 *
 * Nothing is taken when the conversation doesn't accept media (the same gate that hides the
 * Camera and Photos rows), and no more than [remaining] are. An item is an image only if
 * [mimeOf] says an image type for it; an unknown type is not taken.
 */
internal fun selectDroppedImages(
    candidates: List<Uri>,
    acceptsMedia: Boolean,
    remaining: Int,
    mimeOf: (Uri) -> String?,
): List<Uri> {
    if (!acceptsMedia || remaining <= 0) return emptyList()
    return candidates.filter { mimeOf(it)?.startsWith("image/") == true }.take(remaining)
}

/**
 * Copies each of [sources] with [copy] and returns the copies that succeeded, in order.
 *
 * A dropped content URI is readable only under a grant that lives as long as the activity, so the
 * bytes are copied out before anything deferred; the upload pipeline would otherwise read it
 * after a blob policy fetch, possibly after the grant is gone.
 */
internal suspend fun copyDroppedImages(sources: List<Uri>, copy: suspend (Uri) -> Uri?): List<Uri> =
    sources.mapNotNull { runCatching { copy(it) }.getOrNull() }

/** Copies [source] into the camera capture directory and returns the file's URI, null on failure. */
internal suspend fun copyIntoCaptureFile(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
    val file: File = captureFile(context)
    val copied = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
    if (copied && file.length() > 0) {
        Uri.fromFile(file)
    } else {
        file.delete()
        null
    }
}

/**
 * Receives images dropped (or pasted, or inserted from a keyboard) on whatever this is applied to,
 * and hands app-owned copies of them to [onImages], to be staged like picked photos.
 *
 * `contentReceiver` asks the activity for the drag's URI permission itself on a drop (it calls
 * `DragAndDropPermissionsCompat.request` before `onReceive`), and a text field below this node
 * defers to it, so the field takes drops here as well.
 *
 * Only an image type items are taken. Anything else, and every item when [acceptsMedia] is false, is
 * returned unconsumed so the field can use text or the drop is refused. [onImages] is called with
 * files under the capture directory, which the view model deletes once they are encoded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.composerImageReceiver(
    acceptsMedia: Boolean,
    remaining: Int,
    onImages: (List<Uri>) -> Unit,
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentAccepts by rememberUpdatedState(acceptsMedia)
    val currentRemaining by rememberUpdatedState(remaining)
    val currentOnImages by rememberUpdatedState(onImages)
    val listener = remember(context, scope) {
        ReceiveContentListener { content: TransferableContent ->
            val resolver = context.contentResolver
            val mimeOf: (Uri) -> String? = { runCatching { resolver.getType(it) }.getOrNull() }
            val items = buildList {
                val clip = content.clipEntry.clipData
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add)
            }
            val taken = selectDroppedImages(items, currentAccepts, currentRemaining, mimeOf)
            if (taken.isEmpty()) return@ReceiveContentListener content
            // Every image item is consumed, including any past the limit, so they don't land in the field.
            val rest = content.consume { item -> item.uri?.let { mimeOf(it)?.startsWith("image/") } == true }
            scope.launch {
                val copies = copyDroppedImages(taken) { copyIntoCaptureFile(context, it) }
                if (copies.isNotEmpty()) currentOnImages(copies)
            }
            rest
        }
    }
    return this.contentReceiver(listener)
}
