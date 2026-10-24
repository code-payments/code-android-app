package com.flipcash.app.core.media

/**
 * Whether the image loader already holds the bytes filed under a stable cache key. Blob renditions
 * are cached by their durable blob id rather than their download URL, so a hit here means the URL
 * will never be dereferenced — and an expired one costs nothing.
 */
interface ImageCachePresence {
    suspend fun holds(cacheKey: String): Boolean
}
