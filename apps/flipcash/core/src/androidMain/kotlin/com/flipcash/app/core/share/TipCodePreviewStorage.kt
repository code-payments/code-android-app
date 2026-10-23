package com.flipcash.app.core.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Single source of truth for *where* tip-code share previews live on disk and how they map to
 * `content://` URIs. Shared by the renderer (which writes the files) and the cache (which prunes
 * them) so the two never disagree on paths.
 *
 * Files live under `cacheDir/share_previews/` and are named by the card's content [signature]
 * ([tipCodePreviewSignature]) so identical content reuses the same files and stale content is
 * naturally superseded.
 */
object TipCodePreviewStorage {
    const val SUBDIR = "share_previews"

    fun dir(context: Context): File =
        File(context.cacheDir, SUBDIR).apply { if (!exists()) mkdirs() }

    fun heroFile(context: Context, signature: String): File =
        File(dir(context), "${signature}_hero.png")

    fun squareFile(context: Context, signature: String): File =
        File(dir(context), "${signature}_square.png")

    /** The authority declared for the `FileProvider` in the app manifest. */
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /**
     * Bounds the on-disk footprint of cached previews. Deletes anything older than [maxAge], then —
     * newest first — keeps files until [maxTotalBytes] is reached and deletes the rest. Cheap to
     * call on app start; previews are re-rendered on demand, so over-pruning only costs a re-render.
     *
     * Safe to call off the main thread. Never throws — cleanup failures are best-effort.
     */
    fun prune(
        context: Context,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxAge: Long = DEFAULT_MAX_AGE_MILLIS,
        now: Long = System.currentTimeMillis(),
    ) {
        pruneDirectory(dir(context), maxTotalBytes, maxAge, now)
    }

    private const val DEFAULT_MAX_TOTAL_BYTES = 8L * 1024 * 1024 // 8 MiB
    private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000 // 1 day
}

/**
 * Deletes anything in [dir] older than [maxAge], then -- newest first -- keeps files until
 * [maxTotalBytes] is reached and deletes the rest. Never throws; cleanup is best-effort.
 */
internal fun pruneDirectory(dir: File, maxTotalBytes: Long, maxAge: Long, now: Long) {
    runCatching {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var kept = 0L
        for (file in files) {
            val expired = now - file.lastModified() > maxAge
            kept += file.length()
            if (expired || kept > maxTotalBytes) {
                file.delete()
            }
        }
    }
}
