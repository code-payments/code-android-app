package com.flipcash.app

import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

/**
 * A [CacheStrategy] that treats every cached image as immutable.
 *
 * Blob bytes never change once stored — a blob id addresses one fixed set of bytes forever — and
 * their `download_url`s are ephemeral (the server re-mints and expires them on every fetch). Coil's
 * default strategy is a stock HTTP cache: it honours `Cache-Control`/`Expires`/`ETag` and will
 * revalidate or re-download once a stored response looks stale, so images we already have on disk
 * still get re-fetched across launches — flashing the BlurHash before the same bytes reappear.
 *
 * Since the bytes are static there is nothing to revalidate: if we have a cached response we serve
 * it unconditionally and never hit the network; on a miss we download once and always persist. This
 * is paired with keying the Coil disk/memory cache on the durable blob id rather than the rotating
 * URL (see `MediaItem.cacheKeyForSize`); without a stable key this strategy would have nothing to
 * hit.
 */
internal object ImmutableBlobCacheStrategy : CacheStrategy {

    // Only invoked when a cached response exists — serve it as-is, no revalidation, no network.
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult = CacheStrategy.ReadResult(cacheResponse)

    // Always persist freshly downloaded bytes so the next load is a hit.
    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult = CacheStrategy.WriteResult(networkResponse)
}
