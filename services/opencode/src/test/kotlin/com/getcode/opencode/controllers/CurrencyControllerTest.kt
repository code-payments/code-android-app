package com.getcode.opencode.controllers

import app.cash.turbine.test
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.streamers.ManagedMintStream
import com.getcode.opencode.model.core.errors.CheckTokenAvailabilityError
import com.getcode.opencode.model.core.errors.DiscoverTokensError
import com.getcode.opencode.model.core.errors.GetHistoricalMintDataError
import com.getcode.opencode.model.core.errors.GetMintsError
import com.getcode.opencode.model.core.errors.LaunchTokenError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VerifiedResponseData
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Signature
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A non-USDF mint for tests that need to exercise the reserve-state path. */
private val TestMint = Mint(ByteArray(32) { 0x42 }.toList())
private val TestMint2 = Mint(ByteArray(32) { 0x43 }.toList())

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyControllerTest {

    private val repository = FakeCurrencyRepository()
    private val controller = CurrencyController(repository)

    // region checkTokenAvailability

    @Test
    fun `checkTokenAvailability returns success when name is available`() = runTest {
        repository.checkTokenAvailabilityResult = Result.success(true)

        val result = controller.checkTokenAvailability("mytoken")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `checkTokenAvailability throws Unavailable when name is taken`() = runTest {
        repository.checkTokenAvailabilityResult = Result.success(false)

        val result = controller.checkTokenAvailability("taken")

        assertTrue(result.isFailure)
        assertIs<CheckTokenAvailabilityError.Unavailable>(result.exceptionOrNull())
    }

    @Test
    fun `checkTokenAvailability propagates repository failure`() = runTest {
        val error = RuntimeException("network error")
        repository.checkTokenAvailabilityResult = Result.failure(error)

        val result = controller.checkTokenAvailability("test")

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    // endregion

    // region getMintMetadata

    @Test
    fun `getMintMetadata returns metadata from repository`() = runTest {
        val metadata = listOf(stubMintMetadata(Mint.usdf))
        repository.getMintMetadataResult = Result.success(metadata)

        val result = controller.getMintMetadata(listOf(Mint.usdf))

        assertTrue(result.isSuccess)
        assertEquals(metadata, result.getOrThrow())
    }

    @Test
    fun `getMintMetadata propagates repository failure`() = runTest {
        val error = GetMintsError.NotFound()
        repository.getMintMetadataResult = Result.failure(error)

        val result = controller.getMintMetadata(listOf(Mint.usdf))

        assertTrue(result.isFailure)
        assertIs<GetMintsError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region getHistoricalMintData

    @Test
    fun `getHistoricalMintData returns data from repository`() = runTest {
        val data = listOf(
            HistoricalMintData(Instant.fromEpochMilliseconds(1000), 100.0)
        )
        repository.getHistoricalMintDataResult = Result.success(data)

        val result = controller.getHistoricalMintData(Mint.usdf, CurrencyCode.USD, WindowedRange.LastDay)

        assertTrue(result.isSuccess)
        assertEquals(data, result.getOrThrow())
    }

    @Test
    fun `getHistoricalMintData propagates repository failure`() = runTest {
        val error = GetHistoricalMintDataError.NotFound()
        repository.getHistoricalMintDataResult = Result.failure(error)

        val result = controller.getHistoricalMintData(Mint.usdf, CurrencyCode.USD, WindowedRange.AllTime)

        assertTrue(result.isFailure)
        assertIs<GetHistoricalMintDataError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region discoverTokens

    @Test
    fun `discoverTokens returns tokens for Popular category`() = runTest {
        val tokens = listOf(stubMintMetadata(Mint.usdf))
        repository.discoverTokensResult = Result.success(tokens)

        val result = controller.discoverTokens(DiscoverCategory.Popular)

        assertTrue(result.isSuccess)
        assertEquals(tokens, result.getOrThrow())
    }

    @Test
    fun `discoverTokens returns tokens for New category`() = runTest {
        val tokens = listOf(stubMintMetadata(Mint.usdf))
        repository.discoverTokensResult = Result.success(tokens)

        val result = controller.discoverTokens(DiscoverCategory.New)

        assertTrue(result.isSuccess)
        assertEquals(tokens, result.getOrThrow())
    }

    @Test
    fun `discoverTokens propagates repository failure`() = runTest {
        val error = DiscoverTokensError.NotFound()
        repository.discoverTokensResult = Result.failure(error)

        val result = controller.discoverTokens(DiscoverCategory.Popular)

        assertTrue(result.isFailure)
        assertIs<DiscoverTokensError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region launchToken

    @Test
    fun `launchToken passes params through and returns mint`() = runTest {
        val owner = mockk<Ed25519.KeyPair>()
        repository.launchTokenResult = Result.success(Mint.usdf)

        val result = controller.launchToken("name", "SYM", "desc", null, null, owner)

        assertTrue(result.isSuccess)
        assertEquals(Mint.usdf, result.getOrThrow())
    }

    @Test
    fun `launchToken propagates repository failure`() = runTest {
        val owner = mockk<Ed25519.KeyPair>()
        val error = LaunchTokenError.Denied()
        repository.launchTokenResult = Result.failure(error)

        val result = controller.launchToken("name", "SYM", "desc", null, null, owner)

        assertTrue(result.isFailure)
        assertIs<LaunchTokenError.Denied>(result.exceptionOrNull())
    }

    // endregion

    // region getLiveMintData

    @Test
    fun `getLiveMintData completes after ExchangeRates and ReserveState for non-USDF`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        repository.streamMintDataHandler = { _, _, _, onUpdate ->
            onUpdate(LiveMintDataResponse.ExchangeRates(emptyList()))
            onUpdate(LiveMintDataResponse.LaunchpadReserveState(emptyList()))
        }

        val result = controller.getLiveMintData(scope = testScope, mint = TestMint)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getLiveMintData completes after only ExchangeRates for USDF`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        repository.streamMintDataHandler = { _, _, _, onUpdate ->
            onUpdate(LiveMintDataResponse.ExchangeRates(emptyList()))
        }

        val result = controller.getLiveMintData(scope = testScope, mint = Mint.usdf)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getLiveMintData propagates stream error`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        repository.streamMintDataHandler = { _, _, _, _ ->
            throw RuntimeException("stream failed")
        }

        val result = controller.getLiveMintData(scope = testScope, mint = TestMint)

        assertTrue(result.isFailure)
        assertEquals("stream failed", result.exceptionOrNull()?.message)
    }

    // endregion

    // region streamLiveMintData

    @Test
    fun `streamLiveMintData emits empty flow for empty mint list`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val mints = flowOf(emptyList<Mint>())

        controller.streamLiveMintData(scope = testScope, mints = mints).test {
            awaitComplete()
        }
    }

    @Test
    fun `streamLiveMintData emits data from callback`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val expectedResponse = LiveMintDataResponse.ExchangeRates(emptyList())

        repository.streamMintDataHandler = { _, _, _, onUpdate ->
            onUpdate(expectedResponse)
        }

        val mints = flowOf(listOf(Mint.usdf))

        controller.streamLiveMintData(scope = testScope, mints = mints).test {
            val item = awaitItem()
            assertIs<LiveMintDataResponse.ExchangeRates>(item)
        }
    }

    @Test
    fun `streamLiveMintData re-subscribes on new mint list`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        val response1 = LiveMintDataResponse.ExchangeRates(
            listOf(
                VerifiedResponseData.ExchangeRate(
                    rate = Rate(fx = 1.0, currency = CurrencyCode.USD),
                    timestamp = Instant.fromEpochMilliseconds(1000),
                    signature = Signature.zero,
                )
            )
        )
        val response2 = LiveMintDataResponse.ExchangeRates(
            listOf(
                VerifiedResponseData.ExchangeRate(
                    rate = Rate(fx = 2.0, currency = CurrencyCode.EUR),
                    timestamp = Instant.fromEpochMilliseconds(2000),
                    signature = Signature.zero,
                )
            )
        )

        repository.streamMintDataHandler = { _, mints, _, onUpdate ->
            if (mints.contains(TestMint)) {
                onUpdate(response1)
            } else if (mints.contains(TestMint2)) {
                onUpdate(response2)
            }
        }

        val mintFlow = MutableStateFlow(listOf(TestMint))

        controller.streamLiveMintData(scope = testScope, mints = mintFlow).test {
            val first = assertIs<LiveMintDataResponse.ExchangeRates>(awaitItem())
            assertEquals(1.0, first.rates.first().rate.fx)

            mintFlow.value = listOf(TestMint2)

            val second = assertIs<LiveMintDataResponse.ExchangeRates>(awaitItem())
            assertEquals(2.0, second.rates.first().rate.fx)
        }
    }

    // endregion

    // region helpers

    private fun stubMintMetadata(mint: Mint): MintMetadata {
        return MintMetadata(
            address = mint,
            decimals = 5,
            name = "Test Token",
            symbol = "TST",
            createdAt = null,
            description = "A test token",
            imageUrl = "",
            vmMetadata = mockk(relaxed = true),
            launchpadMetadata = null,
            billCustomizations = null,
            socialLinks = emptyList(),
            holderMetrics = HolderMetrics.None,
        )
    }

    // endregion
}

/**
 * Fake [CurrencyRepository] for testing. MockK cannot mock methods returning
 * [kotlin.Result] because it is a compiler-intrinsic inline class — not a
 * regular value class — and MockK's value-class support does not cover it.
 * See https://github.com/mockk/mockk/issues/858
 */
private class FakeCurrencyRepository : CurrencyRepository {
    var checkTokenAvailabilityResult: Result<Boolean> = Result.success(true)
    var getMintMetadataResult: Result<List<MintMetadata>> = Result.success(emptyList())
    var getHistoricalMintDataResult: Result<List<HistoricalMintData>> = Result.success(emptyList())
    var discoverTokensResult: Result<List<Token>> = Result.success(emptyList())
    var launchTokenResult: Result<Mint> = Result.success(Mint.usdf)

    var streamMintDataHandler: (CoroutineScope, List<Mint>, String?, (LiveMintDataResponse) -> Unit) -> Unit =
        { _, _, _, _ -> }

    private val noOpStream = mockk<ManagedMintStream>(relaxed = true)

    override fun streamMintData(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String?,
        onUpdate: (LiveMintDataResponse) -> Unit,
    ): ManagedMintStream {
        streamMintDataHandler(scope, mints, tag, onUpdate)
        return noOpStream
    }

    override suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>> =
        getMintMetadataResult

    override suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> = getHistoricalMintDataResult

    override suspend fun discoverTokens(category: DiscoverCategory): Result<List<Token>> =
        discoverTokensResult

    override suspend fun checkTokenAvailability(name: String): Result<Boolean> =
        checkTokenAvailabilityResult

    override suspend fun launchToken(
        name: String,
        symbol: String,
        description: String,
        bill: TokenBillCustomizations?,
        icon: ByteArray?,
        owner: Ed25519.KeyPair,
    ): Result<Mint> = launchTokenResult
}
