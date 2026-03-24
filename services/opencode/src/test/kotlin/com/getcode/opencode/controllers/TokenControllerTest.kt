package com.getcode.opencode.controllers

import app.cash.turbine.test
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.streamers.ManagedMintStream
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetMintsError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import com.getcode.utils.network.NetworkObserverStub
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TokenControllerTest {

    private val currencyRepository = FakeTokenControllerCurrencyRepository()
    private val accountRepository = FakeTokenControllerAccountRepository()
    private val networkObserver = NetworkObserverStub(isConnected = false)

    private val currencyController = CurrencyController(currencyRepository)
    private val accountController = AccountController(accountRepository, networkObserver)
    private val controller = TokenController(accountController, currencyController)

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    // region getTokenMetadata (single)

    @Test
    fun `getTokenMetadata for single mint returns TokenResult with Network source`() = runTest {
        val metadata = stubMintMetadata(Mint.usdf)
        currencyRepository.getMintMetadataResult = Result.success(listOf(metadata))

        val result = controller.getTokenMetadata(Mint.usdf)

        assertTrue(result.isSuccess)
        val tokenResult = result.getOrThrow()
        assertEquals(Mint.usdf, tokenResult.token.address)
        assertEquals(DataSource.Network, tokenResult.source)
    }

    @Test
    fun `getTokenMetadata for single mint propagates failure`() = runTest {
        currencyRepository.getMintMetadataResult = Result.failure(GetMintsError.NotFound())

        val result = controller.getTokenMetadata(Mint.usdf)

        assertTrue(result.isFailure)
        assertIs<GetMintsError.NotFound>(result.exceptionOrNull())
    }

    // endregion

    // region getTokenMetadata (batch)

    @Test
    fun `getTokenMetadata for multiple mints returns all metadata`() = runTest {
        val metadata = listOf(stubMintMetadata(Mint.usdf), stubMintMetadata(Mint.usdc))
        currencyRepository.getMintMetadataResult = Result.success(metadata)

        val result = controller.getTokenMetadata(listOf(Mint.usdf, Mint.usdc))

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `getTokenMetadata batch propagates failure`() = runTest {
        currencyRepository.getMintMetadataResult = Result.failure(GetMintsError.NotFound())

        val result = controller.getTokenMetadata(listOf(Mint.usdf))

        assertTrue(result.isFailure)
    }

    // endregion

    // region getHistoricalMarketCapData

    @Test
    fun `getHistoricalMarketCapData delegates to currencyController`() = runTest {
        val data = listOf(
            HistoricalMintData(Instant.fromEpochMilliseconds(1000), 100.0)
        )
        currencyRepository.getHistoricalMintDataResult = Result.success(data)

        val result = controller.getHistoricalMarketCapData(
            Mint.usdf, CurrencyCode.USD, WindowedRange.LastDay
        )

        assertTrue(result.isSuccess)
        assertEquals(data, result.getOrThrow())
    }

    @Test
    fun `getHistoricalMarketCapData propagates failure`() = runTest {
        currencyRepository.getHistoricalMintDataResult =
            Result.failure(RuntimeException("no data"))

        val result = controller.getHistoricalMarketCapData(
            Mint.usdf, CurrencyCode.USD, WindowedRange.AllTime
        )

        assertTrue(result.isFailure)
    }

    // endregion

    // region streamReserveStates

    @Test
    fun `streamReserveStates filters for LaunchpadReserveState only`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        currencyRepository.streamMintDataHandler = { _, _, _, onUpdate ->
            onUpdate(LiveMintDataResponse.ExchangeRates(emptyList()))
            onUpdate(LiveMintDataResponse.LaunchpadReserveState(emptyList()))
        }

        val mints = flowOf(listOf(Mint.usdf))

        controller.streamReserveStates(scope = testScope, mints = mints).test {
            val item = awaitItem()
            assertIs<LiveMintDataResponse.LaunchpadReserveState>(item)
        }
    }

    @Test
    fun `streamReserveStates completes for empty mint list`() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val mints = flowOf(emptyList<Mint>())

        controller.streamReserveStates(scope = testScope, mints = mints).test {
            awaitComplete()
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

// region Fakes for TokenController dependencies

private class FakeTokenControllerCurrencyRepository : CurrencyRepository {
    var getMintMetadataResult: Result<List<MintMetadata>> = Result.success(emptyList())
    var getHistoricalMintDataResult: Result<List<HistoricalMintData>> = Result.success(emptyList())
    var discoverTokensResult: Result<List<Token>> = Result.success(emptyList())
    var checkTokenAvailabilityResult: Result<Boolean> = Result.success(true)
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

    override suspend fun getMintMetadata(addresses: List<Mint>) = getMintMetadataResult
    override suspend fun getHistoricalMintData(
        mint: Mint, currencyCode: CurrencyCode, windowedRange: WindowedRange,
    ) = getHistoricalMintDataResult

    override suspend fun discoverTokens(category: DiscoverCategory) = discoverTokensResult
    override suspend fun checkTokenAvailability(name: String) = checkTokenAvailabilityResult
    override suspend fun launchToken(
        name: String, symbol: String, description: String,
        bill: TokenBillCustomizations?, icon: ByteArray?, owner: Ed25519.KeyPair,
    ) = launchTokenResult
}

private class FakeTokenControllerAccountRepository : AccountRepository {
    override suspend fun isValidAccount(owner: Ed25519.KeyPair) = Result.success(true)
    override suspend fun createUserAccount(
        scope: CoroutineScope, ownerForMint: AccountCluster, mint: Mint,
    ): Result<ID> = Result.success(emptyList())
    override suspend fun getAccounts(
        accountOwner: Ed25519.KeyPair, requestingOwner: Ed25519.KeyPair, filter: AccountFilter?,
    ): Result<AccountResponse> = Result.success(AccountResponse(emptyMap()))
    override suspend fun getAccount(
        accountOwner: Ed25519.KeyPair, requestingOwner: Ed25519.KeyPair, filter: AccountFilter,
    ): Result<AccountInfo> = Result.failure(RuntimeException("not configured"))
}

// endregion
