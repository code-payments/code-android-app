package com.flipcash.app

import android.content.Context
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A cached failure is the one thing this strategy must not treat as immutable: `NetworkFetcher`
 * writes the response to the disk cache before it throws on the status code, and it re-throws
 * whatever the strategy hands back from the cache without making a request. Serve a stored 403 and
 * that blob is unloadable for the life of the cache entry, however fresh a download URL it is given.
 */
class ImmutableBlobCacheStrategyTest {

    private val request = NetworkRequest(url = "https://cdn.example/images/blob/thumbnail_160x160.webp")
    private val options = Options(context = mockk<Context>())

    @Test
    fun `a cached success is served without a request`() = runTest {
        val cached = NetworkResponse(code = 200)

        val result = ImmutableBlobCacheStrategy.read(cached, request, options)

        assertSame(cached, result.response)
        assertNull(result.request)
    }

    @Test
    fun `a cached failure falls through to the network`() = runTest {
        for (code in listOf(403, 404, 500)) {
            val result = ImmutableBlobCacheStrategy.read(NetworkResponse(code = code), request, options)

            assertNull(result.response, "$code was served from the cache")
            assertSame(request, result.request, "$code did not issue a request")
        }
    }

    @Test
    fun `a success is persisted`() = runTest {
        val response = NetworkResponse(code = 200)

        val result = ImmutableBlobCacheStrategy.write(null, request, response, options)

        assertSame(response, result.response)
    }

    @Test
    fun `a failure is not persisted`() = runTest {
        for (code in listOf(403, 404, 500)) {
            val result = ImmutableBlobCacheStrategy.write(null, request, NetworkResponse(code = code), options)

            assertNull(result.response, "$code was written to the cache")
        }
    }

    @Test
    fun `a not modified response is served, matching what the fetcher accepts`() = runTest {
        val cached = NetworkResponse(code = 304)

        assertEquals(cached, ImmutableBlobCacheStrategy.read(cached, request, options).response)
    }
}
