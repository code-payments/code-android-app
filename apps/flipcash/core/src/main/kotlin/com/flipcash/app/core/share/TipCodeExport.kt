package com.flipcash.app.core.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A user-facing export of a tip code: the scannable graphic on its own, written to a file the
 * Sharesheet (or a "save to Files" target) can consume.
 *
 * Distinct from [TipCodePreview], which exists only to give the Sharesheet a thumbnail while the
 * thing actually shared stays the tip URL. An export *is* the payload.
 */
data class TipCodeExport(
    val uri: Uri,
    val format: TipCodeExportFormat,
) {
    val mimeType: String get() = format.mimeType
}

enum class TipCodeExportFormat(val extension: String, val mimeType: String) {
    /** Raster. Universally pasteable; fixed resolution. */
    Png("png", "image/png"),

    /**
     * Vector. Scales to any size (print, large-format) and stays a few KB.
     *
     * Self-contained by construction — no fonts, no embedded raster, no external references —
     * because the export is code-only, so there is no text to worry about.
     */
    Svg("svg", "image/svg+xml"),
}

/**
 * Where exported codes live on disk and how they map to `content://` URIs.
 *
 * Separate directory from [TipCodePreviewStorage] so the preview cache's aggressive pruning can't
 * delete an export out from under a share in progress, and so the two can be tuned independently.
 * Files are named by the card's content [tipCodePreviewSignature], so re-exporting the same card
 * overwrites rather than accumulates.
 */
object TipCodeExportStorage {
    const val SUBDIR = "share_exports"

    fun dir(context: Context): File =
        File(context.cacheDir, SUBDIR).apply { if (!exists()) mkdirs() }

    /**
     * Name is user-visible in some share targets ("save to Files"), hence the readable prefix
     * rather than a bare hash.
     */
    fun file(
        context: Context,
        signature: String,
        format: TipCodeExportFormat,
        baseName: String? = null,
    ): File = File(dir(context), "${exportFileBaseName(baseName) ?: "flipcash-code-$signature"}.${format.extension}")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, TipCodePreviewStorage.authority(context), file)

    /** See [TipCodePreviewStorage.prune]; exports are re-creatable, so over-pruning only costs a re-render. */
    fun prune(
        context: Context,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxAge: Long = DEFAULT_MAX_AGE_MILLIS,
        now: Long = System.currentTimeMillis(),
    ) = pruneDirectory(dir(context), maxTotalBytes, maxAge, now)

    private const val DEFAULT_MAX_TOTAL_BYTES = 8L * 1024 * 1024 // 8 MiB
    private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000 // 1 day
}

private const val MaxBaseNameLength = 80
private val HostileFileNameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

/**
 * [raw] made safe to use as a file name, or null when nothing usable is left. Drops path separators
 * and the characters common file systems reject, trims, and caps the length without splitting a
 * surrogate pair.
 */
fun exportFileBaseName(raw: String?): String? {
    val cleaned = raw.orEmpty().replace(HostileFileNameChars, "").trim()
    if (cleaned.isEmpty()) return null
    var end = minOf(cleaned.length, MaxBaseNameLength)
    if (end < cleaned.length && Character.isHighSurrogate(cleaned[end - 1])) end--
    return cleaned.substring(0, end).trim().ifEmpty { null }
}
