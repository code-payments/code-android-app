package com.flipcash.app.onramp

import com.coinbase.onramp.api.CoinbaseApi
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BuyOptionsCacheTest {

    private val api = mockk<CoinbaseApi>(relaxed = true)
    private val jwtExecutor = mockk<CoinbaseJwtExecutor>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)

    private lateinit var cache: BuyOptionsCache

    @Before
    fun setUp() {
        cache = BuyOptionsCache(api, jwtExecutor, userManager)
    }

    private fun buyOptionsResponse(vararg symbols: String): JsonObject = JsonObject(
        mapOf(
            "purchase_currencies" to JsonArray(
                symbols.map { JsonObject(mapOf("symbol" to JsonPrimitive(it))) }
            )
        )
    )

    private fun stubApi(response: JsonObject) {
        coEvery {
            jwtExecutor.execute(any(), any(), any(), any(), any<suspend (String) -> Result<JsonObject>>())
        } coAnswers {
            val call = arg<suspend (String) -> Result<JsonObject>>(4)
            call("test-jwt")
        }
        coEvery {
            api.getBuyOptions(url = any(), jwt = any(), country = any(), subdivision = any())
        } returns response
        coEvery {
            api.getBuyOptions(url = any(), jwt = any(), country = any(), subdivision = isNull())
        } returns response
    }

    @Test
    fun `parseMints extracts correct symbols from response`() = runTest {
        val response = buyOptionsResponse("USDC", "USDF", "BTC")
        stubApi(response)

        val region = PhoneRegion("US")
        val mints = cache.prefetch(region)

        assertEquals(
            setOf(BuyOptionsMint("USDC"), BuyOptionsMint("USDF"), BuyOptionsMint("BTC")),
            mints
        )
    }

    @Test
    fun `second call returns cached result without hitting API`() = runTest {
        val response = buyOptionsResponse("USDC", "USDF")
        stubApi(response)

        val region = PhoneRegion("US")
        cache.prefetch(region)
        cache.prefetch(region)

        coVerify(exactly = 1) { api.getBuyOptions(any(), any(), any(), any()) }
    }

    @Test
    fun `getCached returns null before prefetch`() {
        val region = PhoneRegion("US")
        assertNull(cache.getCached(region))
    }

    @Test
    fun `getCached returns mints after prefetch`() = runTest {
        val response = buyOptionsResponse("USDC", "USDF")
        stubApi(response)

        val region = PhoneRegion("US")
        cache.prefetch(region)

        assertEquals(
            setOf(BuyOptionsMint("USDC"), BuyOptionsMint("USDF")),
            cache.getCached(region)
        )
    }

    @Test
    fun `isUsdfAvailable returns true when USDF in cache`() = runTest {
        val response = buyOptionsResponse("USDC", "USDF")
        stubApi(response)

        val region = PhoneRegion("US")
        cache.prefetch(region)

        assertTrue(cache.isUsdfAvailable(region))
    }

    @Test
    fun `isUsdfAvailable returns false when USDF not in cache`() = runTest {
        val response = buyOptionsResponse("USDC", "BTC")
        stubApi(response)

        val region = PhoneRegion("US")
        cache.prefetch(region)

        assertEquals(false, cache.isUsdfAvailable(region))
    }

    @Test
    fun `isUsdfAvailable defaults to true on cache miss`() {
        val region = PhoneRegion("US")
        assertTrue(cache.isUsdfAvailable(region))
    }

    @Test
    fun `prefetchForCurrentUser no-ops when no verified phone`() = runTest {
        every { userManager.profile } returns null

        val result = cache.prefetchForCurrentUser()
        assertNull(result)
        coVerify(exactly = 0) { api.getBuyOptions(any(), any(), any(), any()) }
    }

    @Test
    fun `prefetchForCurrentUser no-ops when phone has no verified number`() = runTest {
        every { userManager.profile } returns UserProfile(
            displayName = "Test",
            socialAccounts = emptyList(),
            verifiedEmailAddress = "test@test.com",
            verifiedPhoneNumber = null,
        )

        val result = cache.prefetchForCurrentUser()
        assertNull(result)
        coVerify(exactly = 0) { api.getBuyOptions(any(), any(), any(), any()) }
    }

    @Test
    fun `prefetchForCurrentUser fetches for detected region`() = runTest {
        every { userManager.profile } returns UserProfile(
            displayName = "Test",
            socialAccounts = emptyList(),
            verifiedEmailAddress = "test@test.com",
            verifiedPhoneNumber = "+14155551234",
        )

        val response = buyOptionsResponse("USDC", "USDF")
        stubApi(response)

        val result = cache.prefetchForCurrentUser()
        assertEquals(setOf(BuyOptionsMint("USDC"), BuyOptionsMint("USDF")), result)
    }

    @Test
    fun `prefetch returns null on JWT failure`() = runTest {
        coEvery {
            jwtExecutor.execute<JsonObject>(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("jwt fail"))

        val region = PhoneRegion("US")
        val result = cache.prefetch(region)
        assertNull(result)
    }

    @Test
    fun `prefetch returns null on API failure`() = runTest {
        coEvery {
            jwtExecutor.execute(any(), any(), any(), any(), any<suspend (String) -> Result<JsonObject>>())
        } coAnswers {
            val call = arg<suspend (String) -> Result<JsonObject>>(4)
            call("test-jwt")
        }
        coEvery { api.getBuyOptions(any(), any(), any(), any()) } throws RuntimeException("api fail")
        coEvery { api.getBuyOptions(any(), any(), any(), subdivision = isNull()) } throws RuntimeException("api fail")

        val region = PhoneRegion("US")
        val result = cache.prefetch(region)
        assertNull(result)
    }
}
