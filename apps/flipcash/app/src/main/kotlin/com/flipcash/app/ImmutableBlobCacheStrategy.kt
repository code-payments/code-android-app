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
 * Since the bytes are static there is nothing to revalidate: if we have the bytes we serve them
 * unconditionally and never hit the network; on a miss we download once and persist. This is paired
 * with keying the Coil disk/memory cache on the durable blob id rather than the rotating URL (see
 * `MediaItem.cacheKeyForSize`); without a stable key this strategy would have nothing to hit.
 *
 * "Immutable" covers the bytes, not the failures. `NetworkFetcher` writes a response to the disk
 * cache *before* it throws on the status code, so a fetch through an expired URL leaves a cached 403
 * under the blob's key — and a strategy that serves any cached response replays that 403 on every
 * later load without a request, which no amount of re-minting can recover from. So a response that
 * would make `NetworkFetcher` throw is neither served nor stored.
 */
internal object ImmutableBlobCacheStrategy : CacheStrategy {

    // Only invoked when a cached response exists — serve the bytes as-is, no revalidation, no
    // network. A cached failure isn't bytes: fall through to the network so a re-minted URL gets
    // its chance, which is also what un-poisons an entry an older build already wrote.
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult = if (cacheResponse.isUsable) {
        CacheStrategy.ReadResult(cacheResponse)
    } else {
        CacheStrategy.ReadResult(networkRequest)
    }

    // Always persist freshly downloaded bytes so the next load is a hit. Declining a failure also
    // leaves any entry already on disk intact, since the write is what would replace it.
    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult = if (networkResponse.isUsable) {
        CacheStrategy.WriteResult(networkResponse)
    } else {
        CacheStrategy.WriteResult.DISABLED
    }

    /** Mirrors the status codes `NetworkFetcher` accepts instead of throwing `HttpException`. */
    private val NetworkResponse.isUsable: Boolean
        get() = code in 200 until 300 || code == HTTP_NOT_MODIFIED

    private const val HTTP_NOT_MODIFIED = 304
}
