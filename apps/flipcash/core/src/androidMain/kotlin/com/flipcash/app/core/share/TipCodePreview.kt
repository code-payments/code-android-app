package com.flipcash.app.core.share

import android.net.Uri
import com.flipcash.app.core.bill.Scannable
import java.security.MessageDigest

/**
 * A pair of pre-rendered images of a tip code, used to give the Android Sharesheet a rich preview.
 *
 * These are *previews only* — the thing actually shared stays the tip URL (see the share entry
 * point). [heroUri] is a ~1200x630 landscape image (the shape link-unfurlers expect); [squareUri]
 * is a 1:1 recomposition for surfaces that want a square thumbnail. Both are `content://` URIs
 * backed by the app's `FileProvider`.
 *
 * [signature] is a stable content hash of the source card — it names the backing files and lets the
 * cache detect when a cached preview no longer matches the current card (see [tipCodePreviewSignature]).
 */
data class TipCodePreview(
    val heroUri: Uri,
    val squareUri: Uri,
    val signature: String,
)

/**
 * Stable, deterministic content hash of a tip card. Same visible content -> same signature across
 * processes, so rendered files can be reused and cache freshness can be checked by comparison.
 *
 * Hashes only the scannable payload — the share image is just the code, so it's independent of the
 * display name. Kept in `core` (not the renderer) so the cache and every renderer agree on it.
 */
fun tipCodePreviewSignature(card: Scannable.TipCard): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(card.data.toByteArray())
    // 8 bytes of hex is ample to avoid collisions for a per-user preview cache.
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
