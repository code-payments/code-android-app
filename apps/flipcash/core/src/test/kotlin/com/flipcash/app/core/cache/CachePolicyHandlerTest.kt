package com.flipcash.app.core.cache

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachePolicyHandlerTest {

    private val handler = CachePolicyHandler<String, String>()

    // region CacheOnly

    @Test
    fun `CacheOnly with cache hit returns success with Cache origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.CacheOnly,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("cached", entry.data)
        assertEquals(DataOrigin.Cache, entry.origin)
    }

    @Test
    fun `CacheOnly with cache miss returns failure`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.CacheOnly,
            cacheLookup = { null },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isFailure)
    }

    // endregion

    // region CacheFirst

    @Test
    fun `CacheFirst with cache hit returns success with Cache origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.CacheFirst,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("cached", entry.data)
        assertEquals(DataOrigin.Cache, entry.origin)
    }

    @Test
    fun `CacheFirst with cache miss and network success returns success with Network origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.CacheFirst,
            cacheLookup = { null },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("network", entry.data)
        assertEquals(DataOrigin.Network, entry.origin)
    }

    @Test
    fun `CacheFirst with cache miss and network failure returns failure`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.CacheFirst,
            cacheLookup = { null },
            persistNetworkData = { },
            networkRequest = { Result.failure(RuntimeException("network error")) },
            domainMapper = { it },
        )

        assertTrue(result.isFailure)
    }

    // endregion

    // region NetworkFirst

    @Test
    fun `NetworkFirst with network success returns success with Network origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.NetworkFirst,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("network", entry.data)
        assertEquals(DataOrigin.Network, entry.origin)
    }

    @Test
    fun `NetworkFirst with network failure and cache hit returns success with Cache origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.NetworkFirst,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.failure(RuntimeException("network error")) },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("cached", entry.data)
        assertEquals(DataOrigin.Cache, entry.origin)
    }

    @Test
    fun `NetworkFirst with network failure and cache miss returns failure`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.NetworkFirst,
            cacheLookup = { null },
            persistNetworkData = { },
            networkRequest = { Result.failure(RuntimeException("network error")) },
            domainMapper = { it },
        )

        assertTrue(result.isFailure)
    }

    // endregion

    // region NetworkOnly

    @Test
    fun `NetworkOnly with network success returns success with Network origin`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.NetworkOnly,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.success("network") },
            domainMapper = { it },
        )

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("network", entry.data)
        assertEquals(DataOrigin.Network, entry.origin)
    }

    @Test
    fun `NetworkOnly with network failure returns failure`() = runTest {
        val result = handler.execute(
            cachePolicy = CachePolicy.NetworkOnly,
            cacheLookup = { "cached" },
            persistNetworkData = { },
            networkRequest = { Result.failure(RuntimeException("network error")) },
            domainMapper = { it },
        )

        assertTrue(result.isFailure)
    }

    // endregion

    // region persistNetworkData

    @Test
    fun `persistNetworkData is called when network succeeds for NetworkOnly`() = runTest {
        var persisted: String? = null

        handler.execute(
            cachePolicy = CachePolicy.NetworkOnly,
            cacheLookup = { null },
            persistNetworkData = { persisted = it },
            networkRequest = { Result.success("network-data") },
            domainMapper = { it },
        )

        assertEquals("network-data", persisted)
    }

    @Test
    fun `persistNetworkData is called when network succeeds for NetworkFirst`() = runTest {
        var persisted: String? = null

        handler.execute(
            cachePolicy = CachePolicy.NetworkFirst,
            cacheLookup = { null },
            persistNetworkData = { persisted = it },
            networkRequest = { Result.success("network-data") },
            domainMapper = { it },
        )

        assertEquals("network-data", persisted)
    }

    @Test
    fun `persistNetworkData is called when CacheFirst falls back to network`() = runTest {
        var persisted: String? = null

        handler.execute(
            cachePolicy = CachePolicy.CacheFirst,
            cacheLookup = { null },
            persistNetworkData = { persisted = it },
            networkRequest = { Result.success("network-data") },
            domainMapper = { it },
        )

        assertEquals("network-data", persisted)
    }

    // endregion
}
