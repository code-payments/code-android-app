package com.flipcash.app.core.share

import com.flipcash.app.core.bill.Scannable

/**
 * Renders a tip code into shareable preview images (see [TipCodePreview]).
 *
 * This is the seam that keeps the Sharesheet plumbing renderer-agnostic: the Compose-backed
 * implementation lives in `:apps:flipcash:shared:bills`, and a future CMP/shared-UI renderer can be
 * bound in its place without touching the cache or the share intent.
 *
 * Implementations must be resilient: rendering may not be possible right now (e.g. no foreground
 * window). Return `null` in that case rather than throwing — the caller degrades to sharing the URL
 * alone and never blocks the Sharesheet.
 */
interface TipCodePreviewRenderer {
    suspend fun render(card: Scannable.TipCard): TipCodePreview?
}
