package com.flipcash.app.core.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

/**
 * Puts a remote image on disk as a `content://` URI the Sharesheet can read, so a share can carry a
 * thumbnail of something the app only holds as a URL.
 *
 * The Sharesheet draws its preview from the intent's ClipData, and ClipData needs a URI a *different*
 * process can open — a Coil-cached bitmap in this process is not one. So the image is loaded through
 * the app's shared loader (cache-first, keyed on the durable blob id so it hits the rendition the
 * screen already downloaded) and written into the same `share_previews` cache the tip-code previews
 * use, which gets the FileProvider path and the pruning for free.
 *
 * Everything here is best-effort: a slow network, an expired download URL, or a full disk all return
 * null, and a share with no thumbnail is the same share.
 */
object SharePreviewImage {

    /**
     * Longest side to request. The Sharesheet's thumbnail slot is small, and the server ships a 320
     * rendition — asking for more downloads a bigger image to draw it at the same size.
     */
    const val TARGET_PX = 320

    /** How long a share is willing to wait on the image before going without one. */
    private const val FETCH_TIMEOUT_MS = 3_000L

    /**
     * Caches the image at [url], returning the URI to hand to `ClipData`, or null if it could not be
     * fetched or written.
     *
     * [cacheKey] is the rendition's stable identity ([com.flipcash.services.models.chat.MediaItem]'s
     * `cacheKeyForSize`), used both to hit Coil's caches and to name the file — the download URL is
     * re-minted on every fetch, so it names nothing stable.
     */
    suspend fun cache(context: Context, url: String, cacheKey: String? = null): Uri? {
        val file = File(dir(context), "${signature(cacheKey ?: url)}.png")
        // Already written by an earlier share of the same rendition. Touch it so the pruner treats
        // it as recently used rather than as the oldest thing in the directory.
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return uriFor(context, file)
        }

        val bitmap = load(context, url, cacheKey) ?: return null
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uriFor(context, file)
        }.getOrNull()
    }

    private suspend fun load(context: Context, url: String, cacheKey: String?): Bitmap? =
        withTimeoutOrNull(FETCH_TIMEOUT_MS.milliseconds) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .apply { cacheKey?.let { memoryCacheKey(it); diskCacheKey(it) } }
                    // compress() cannot read a hardware bitmap.
                    .allowHardware(false)
                    .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
                    ?.image?.toBitmap()
            }.getOrNull()
        }

    /** Shares the tip-code previews' directory, and with it their FileProvider path and pruning. */
    private fun dir(context: Context): File = TipCodePreviewStorage.dir(context)

    private fun uriFor(context: Context, file: File): Uri =
        TipCodePreviewStorage.uriFor(context, file)

    private fun signature(of: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(of.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
