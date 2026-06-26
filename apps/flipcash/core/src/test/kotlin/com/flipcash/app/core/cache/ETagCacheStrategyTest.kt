package com.flipcash.app.core.cache

import coil3.Extras
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ETagCacheStrategyTest {

    private val strategy = ETagCacheStrategy()

    private fun options(revalidate: Boolean): Options {
        val extras = Extras.Builder()
            .apply { if (revalidate) set(ETagRevalidationKey, true) }
            .build()
        return Options(context = RuntimeEnvironment.getApplication(), extras = extras)
    }

    private fun freshCacheResponse(
        maxAgeSeconds: Long = 3600,
        etag: String? = null,
        lastModified: String? = null,
    ): NetworkResponse {
        val headers = NetworkHeaders.Builder()
            .set("cache-control", "public, max-age=$maxAgeSeconds")
        if (etag != null) headers.set("etag", etag)
        if (lastModified != null) headers.set("last-modified", lastModified)
        return NetworkResponse(
            code = 200,
            responseMillis = System.currentTimeMillis() - 60_000, // 1 minute ago
            headers = headers.build(),
        )
    }

    private fun staleCacheResponse(
        maxAgeSeconds: Long = 3600,
        etag: String? = null,
        lastModified: String? = null,
    ): NetworkResponse {
        val headers = NetworkHeaders.Builder()
            .set("cache-control", "public, max-age=$maxAgeSeconds")
        if (etag != null) headers.set("etag", etag)
        if (lastModified != null) headers.set("last-modified", lastModified)
        return NetworkResponse(
            code = 200,
            responseMillis = System.currentTimeMillis() - (maxAgeSeconds * 1000 + 60_000),
            headers = headers.build(),
        )
    }

    private val baseNetworkRequest = NetworkRequest(url = "https://example.com/icon.png")

    // region key disabled

    @Test
    fun `without key returns cache response`() = runTest {
        val result = strategy.read(staleCacheResponse(), baseNetworkRequest, options(false))
        assertNotNull(result.response)
        assertNull(result.request)
    }

    // endregion

    // region freshness

    @Test
    fun `with key and fresh cache returns cache response`() = runTest {
        val cached = freshCacheResponse(etag = "\"abc123\"")
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.response)
        assertNull(result.request)
    }

    @Test
    fun `with key and stale cache revalidates`() = runTest {
        val cached = staleCacheResponse(etag = "\"abc123\"")
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertNull(result.response)
    }

    @Test
    fun `with key and no cache-control header treats as stale`() = runTest {
        val cached = NetworkResponse(
            code = 200,
            responseMillis = System.currentTimeMillis(),
            headers = NetworkHeaders.Builder()
                .set("etag", "\"abc123\"")
                .build(),
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
    }

    @Test
    fun `with key and zero responseMillis treats as stale`() = runTest {
        val cached = NetworkResponse(
            code = 200,
            responseMillis = 0L,
            headers = NetworkHeaders.Builder()
                .set("cache-control", "public, max-age=3600")
                .set("etag", "\"abc123\"")
                .build(),
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
    }

    // endregion

    // region conditional headers

    @Test
    fun `stale cache with etag adds If-None-Match`() = runTest {
        val cached = staleCacheResponse(etag = "\"abc123\"")
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertEquals("\"abc123\"", result.request!!.headers["if-none-match"])
    }

    @Test
    fun `stale cache with last-modified adds If-Modified-Since`() = runTest {
        val cached = staleCacheResponse(lastModified = "Wed, 21 Oct 2015 07:28:00 GMT")
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", result.request!!.headers["if-modified-since"])
    }

    @Test
    fun `stale cache with both headers adds both conditional headers`() = runTest {
        val cached = staleCacheResponse(
            etag = "\"abc123\"",
            lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertEquals("\"abc123\"", result.request!!.headers["if-none-match"])
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", result.request!!.headers["if-modified-since"])
    }

    @Test
    fun `stale cache with no etag or last-modified sends plain request`() = runTest {
        val cached = staleCacheResponse()
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertNull(result.request!!.headers["if-none-match"])
        assertNull(result.request!!.headers["if-modified-since"])
    }

    // endregion

    // region isStale

    @Test
    fun `isStale returns false when within max-age`() {
        val response = freshCacheResponse(maxAgeSeconds = 3600)
        assertFalse(ETagCacheStrategy.isStale(response))
    }

    @Test
    fun `isStale returns true when past max-age`() {
        val response = staleCacheResponse(maxAgeSeconds = 3600)
        assertTrue(ETagCacheStrategy.isStale(response))
    }

    @Test
    fun `isStale returns true when no cache-control`() {
        val response = NetworkResponse(
            code = 200,
            responseMillis = System.currentTimeMillis(),
            headers = NetworkHeaders.EMPTY,
        )
        assertTrue(ETagCacheStrategy.isStale(response))
    }

    @Test
    fun `isStale returns true when responseMillis is zero`() {
        val response = NetworkResponse(
            code = 200,
            responseMillis = 0L,
            headers = NetworkHeaders.Builder()
                .set("cache-control", "public, max-age=3600")
                .build(),
        )
        assertTrue(ETagCacheStrategy.isStale(response))
    }

    // endregion
}
