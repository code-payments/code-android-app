package com.flipcash.app.core.cache

import coil3.Extras
import coil3.annotation.ExperimentalCoilApi
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import java.io.IOException

/**
 * [Extras.Key] that enables ETag-based cache revalidation for an individual image request.
 * When `true`, [ETagCacheStrategy] will attach conditional headers (`If-None-Match` /
 * `If-Modified-Since`) so Coil revalidates against the server instead of blindly returning
 * the disk-cached image.
 */
internal val ETagRevalidationKey = Extras.Key(default = false)

/**
 * Opts this image request into ETag-based cache revalidation.
 *
 * When set, the [ETagCacheStrategy] reads `ETag` and `Last-Modified` values stored in the
 * disk-cache metadata and sends conditional request headers so the server can respond with
 * `304 Not Modified` when the cached image is still fresh.
 *
 * Pair with [ETagOfflineFallbackInterceptor] to gracefully serve stale cache when the
 * network is unavailable.
 */
fun ImageRequest.Builder.etagRevalidation() = apply {
    extras[ETagRevalidationKey] = true
}

/**
 * A [CacheStrategy] that respects `Cache-Control: max-age` and performs conditional
 * revalidation using `ETag` / `Last-Modified` headers stored in Coil's disk-cache metadata.
 *
 * **Read behaviour:**
 * - If [ETagRevalidationKey] is **not** set on the request, the cached response is returned
 *   immediately (same as [CacheStrategy.DEFAULT]).
 * - If the key **is** set but the cached entry is still fresh (within `max-age`), the cached
 *   response is returned without a network trip.
 * - If the entry is stale, cached `etag` and `last-modified` headers are copied into
 *   `If-None-Match` / `If-Modified-Since` on the outgoing [NetworkRequest]. The server can
 *   then respond with `304` to avoid re-downloading the image body.
 *
 * **Write behaviour:** delegates entirely to [CacheStrategy.DEFAULT], which merges headers on
 * `304` responses and writes new bodies on `2xx` responses.
 */
@OptIn(ExperimentalCoilApi::class)
class ETagCacheStrategy : CacheStrategy {

    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        if (!options.getExtra(ETagRevalidationKey)) {
            return CacheStrategy.ReadResult(cacheResponse)
        }

        if (!isStale(cacheResponse)) {
            return CacheStrategy.ReadResult(cacheResponse)
        }

        val cachedHeaders = cacheResponse.headers
        val etag = cachedHeaders["etag"]
        val lastModified = cachedHeaders["last-modified"]

        if (etag == null && lastModified == null) {
            return CacheStrategy.ReadResult(networkRequest)
        }

        val requestHeaders = networkRequest.headers.newBuilder()
        if (etag != null) {
            requestHeaders["if-none-match"] = etag
        }
        if (lastModified != null) {
            requestHeaders["if-modified-since"] = lastModified
        }

        return CacheStrategy.ReadResult(
            networkRequest.copy(headers = requestHeaders.build())
        )
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        return CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
    }

    companion object {
        private val MAX_AGE_REGEX = Regex("""max-age\s*=\s*(\d+)""")

        /**
         * Returns `true` if the cached response has exceeded its `max-age`, or if
         * `responseMillis` is missing (pre-existing cache entries).
         */
        internal fun isStale(cacheResponse: NetworkResponse): Boolean {
            val responseMillis = cacheResponse.responseMillis
            if (responseMillis <= 0L) return true

            val cacheControl = cacheResponse.headers["cache-control"] ?: return true
            val maxAgeSeconds = MAX_AGE_REGEX.find(cacheControl)
                ?.groupValues?.get(1)?.toLongOrNull()
                ?: return true

            val ageMillis = System.currentTimeMillis() - responseMillis
            return ageMillis > maxAgeSeconds * 1000
        }
    }
}

/**
 * A Coil [Interceptor] that provides offline fallback for requests using ETag revalidation.
 *
 * When [ETagRevalidationKey] is enabled, [ETagCacheStrategy] forces a network round-trip for
 * conditional validation. If the device is offline (or the request otherwise fails with an
 * [IOException]), this interceptor retries the request with revalidation **disabled**, allowing
 * the default cache strategy to serve the stale disk-cached image instead of surfacing an error.
 *
 * Requests that do not opt into ETag revalidation are passed through unmodified.
 */
@OptIn(ExperimentalCoilApi::class)
class ETagOfflineFallbackInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (!request.getExtra(ETagRevalidationKey)) {
            return chain.proceed()
        }

        val result = chain.proceed()
        if (result is ErrorResult && result.throwable is IOException) {
            val fallbackRequest = request.newBuilder()
                .apply { extras[ETagRevalidationKey] = false }
                .build()
            return chain.withRequest(fallbackRequest).proceed()
        }

        return result
    }
}
