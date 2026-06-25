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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    private val baseCacheResponse = NetworkResponse(
        code = 200,
        headers = NetworkHeaders.EMPTY,
    )

    private val baseNetworkRequest = NetworkRequest(url = "https://example.com/icon.png")

    @Test
    fun `without key returns cache response`() = runTest {
        val result = strategy.read(baseCacheResponse, baseNetworkRequest, options(false))
        assertNotNull(result.response)
        assertNull(result.request)
    }

    @Test
    fun `with key and cached etag adds If-None-Match`() = runTest {
        val cached = baseCacheResponse.copy(
            headers = NetworkHeaders.Builder()
                .set("etag", "\"abc123\"")
                .build()
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertNull(result.response)
        assertEquals("\"abc123\"", result.request!!.headers["if-none-match"])
    }

    @Test
    fun `with key and cached last-modified adds If-Modified-Since`() = runTest {
        val cached = baseCacheResponse.copy(
            headers = NetworkHeaders.Builder()
                .set("last-modified", "Wed, 21 Oct 2015 07:28:00 GMT")
                .build()
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", result.request!!.headers["if-modified-since"])
    }

    @Test
    fun `with key and both headers adds both conditional headers`() = runTest {
        val cached = baseCacheResponse.copy(
            headers = NetworkHeaders.Builder()
                .set("etag", "\"abc123\"")
                .set("last-modified", "Wed, 21 Oct 2015 07:28:00 GMT")
                .build()
        )
        val result = strategy.read(cached, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertEquals("\"abc123\"", result.request!!.headers["if-none-match"])
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", result.request!!.headers["if-modified-since"])
    }

    @Test
    fun `with key and no cached headers sends plain request`() = runTest {
        val result = strategy.read(baseCacheResponse, baseNetworkRequest, options(true))
        assertNotNull(result.request)
        assertNull(result.response)
        assertNull(result.request!!.headers["if-none-match"])
        assertNull(result.request!!.headers["if-modified-since"])
    }
}
