package com.getcode.opencode.internal.exchange

import com.codeinc.opencode.gen.common.v1.solanaAccountId
import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.launchpadCurrencyReserveState
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedLaunchpadCurrencyReserveState
import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RealVerifiedFiatCalculatorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }
    }

    private lateinit var verifiedStateManager: VerifiedProtoManager
    private lateinit var currencyController: CurrencyController
    private lateinit var calculator: RealVerifiedFiatCalculator

    private val testMint = Mint(List(32) { 1.toByte() })
    private val dummyKey = PublicKey.fromBase58("11111111111111111111111111111111")

    @Before
    fun setUp() {
        verifiedStateManager = mockk(relaxed = true)
        currencyController = mockk(relaxed = true)
        calculator = RealVerifiedFiatCalculator(verifiedStateManager, currencyController)
    }

    // region USDF passthrough

    @Test
    fun `USDF token returns simple LocalFiat without bonding curve`() = runTest {
        val amount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD)
        val token = usdfToken()

        val result = calculator.compute(
            amount = amount,
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertEquals(Mint.usdf, result.localFiat.mint)
        assertEquals(amount.quarks, result.localFiat.underlyingTokenAmount.quarks)
    }

    @Test
    fun `USDF token with non-USD rate converts native amount`() = runTest {
        val amount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.CAD)
        val rate = Rate(fx = 1.35, currency = CurrencyCode.CAD)
        val token = usdfToken()

        val result = calculator.compute(
            amount = amount,
            token = token,
            rate = rate,
            trace = false,
        ).getOrThrow()

        assertEquals(Mint.usdf, result.localFiat.mint)
        assertEquals(CurrencyCode.CAD, result.localFiat.nativeAmount.currencyCode)
    }

    @Test
    fun `USDF succeeds even when verified state is null`() = runTest {
        val amount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD)
        val token = usdfToken()

        every { verifiedStateManager.getVerifiedStateFor(any(), any()) } returns null

        val result = calculator.compute(
            amount = amount,
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        )

        assertTrue(result.isSuccess)
        assertEquals(Mint.usdf, result.getOrThrow().localFiat.mint)
    }

    // endregion

    // region verified supply usage

    @Test
    fun `uses verified supply when available`() = runTest {
        val supply = 1_000_000_000_000L // 1M tokens
        val token = bondingCurveToken(supply = supply)

        // Set up verified state with a DIFFERENT supply
        val verifiedSupply = 2_000_000_000_000L
        stubVerifiedState(CurrencyCode.USD, testMint, verifiedSupply)

        val result = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        // Compute with the token's own supply using a verified state that has matching supply
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val resultWithTokenSupply = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        // Results should differ because different supplies were used
        assertNotEquals(
            result.localFiat.underlyingTokenAmount.quarks,
            resultWithTokenSupply.localFiat.underlyingTokenAmount.quarks,
        )
    }

    @Test
    fun `returns StaleRate failure when verified state is null`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)

        every {
            verifiedStateManager.getVerifiedStateFor(any(), any())
        } returns null

        val result = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        )

        assertTrue(result.isFailure)
        assertIs<ComputeVerifiedFiatError.StaleRate>(result.exceptionOrNull())
    }

    @Test
    fun `falls back to token supply when verified state has no reserve proto`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)

        val stateWithoutReserve = VerifiedState(
            rateProto = verifiedCoreMintFiatExchangeRate {
                exchangeRate = coreMintFiatExchangeRate { currencyCode = "USD" }
            },
            reserveProto = null,
        )
        every {
            verifiedStateManager.getVerifiedStateFor(CurrencyCode.USD, testMint)
        } returns stateWithoutReserve

        val result = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertTrue(result.localFiat.underlyingTokenAmount.quarks > 0)
    }

    // endregion

    // region balance capping

    @Test
    fun `caps amount to balance when balance is smaller`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val largeAmount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val smallBalance = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD)

        val cappedResult = calculator.compute(
            amount = largeAmount,
            token = token,
            balance = smallBalance,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        val directResult = calculator.compute(
            amount = smallBalance,
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        // Capped result should match computing with the balance amount directly
        assertEquals(
            directResult.localFiat.underlyingTokenAmount.quarks,
            cappedResult.localFiat.underlyingTokenAmount.quarks,
        )
    }

    @Test
    fun `does not cap when balance is larger than amount`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD)
        val largeBalance = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)

        val withBalance = calculator.compute(
            amount = amount,
            token = token,
            balance = largeBalance,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        val withoutBalance = calculator.compute(
            amount = amount,
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertEquals(
            withoutBalance.localFiat.underlyingTokenAmount.quarks,
            withBalance.localFiat.underlyingTokenAmount.quarks,
        )
    }

    // endregion

    // region result consistency

    @Test
    fun `result has correct mint`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val result = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertEquals(testMint, result.localFiat.mint)
    }

    @Test
    fun `result underlying amount is positive for positive input`() = runTest {
        val supply = 1_000_000_000_000L
        val token = bondingCurveToken(supply = supply)
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val result = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertTrue(result.localFiat.underlyingTokenAmount.quarks > 0)
        assertTrue(result.localFiat.nativeAmount.quarks > 0)
        assertTrue(result.localFiat.rate.fx > 0)
    }

    @Test
    fun `same supply produces same result regardless of source`() = runTest {
        val supply = 5_000_000_000_000L
        val token = bondingCurveToken(supply = supply)

        // First: use verified supply matching the token supply
        stubVerifiedState(CurrencyCode.USD, testMint, supply)

        val verifiedResult = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        // Second: use verified state with no reserve proto, falls back to token supply
        val stateWithoutReserve = VerifiedState(
            rateProto = verifiedCoreMintFiatExchangeRate {
                exchangeRate = coreMintFiatExchangeRate { currencyCode = "USD" }
            },
            reserveProto = null,
        )
        every {
            verifiedStateManager.getVerifiedStateFor(CurrencyCode.USD, testMint)
        } returns stateWithoutReserve

        val fallbackResult = calculator.compute(
            amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            token = token,
            rate = Rate.oneToOne,
            trace = false,
        ).getOrThrow()

        assertEquals(
            verifiedResult.localFiat.underlyingTokenAmount.quarks,
            fallbackResult.localFiat.underlyingTokenAmount.quarks,
        )
        assertEquals(
            verifiedResult.localFiat.nativeAmount.quarks,
            fallbackResult.localFiat.nativeAmount.quarks,
        )
    }

    // endregion

    // region helpers

    private fun usdfToken(): Token = MintMetadata(
        address = Mint.usdf,
        decimals = 6,
        name = "USDF",
        symbol = "USDF",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = dummyKey,
            authority = dummyKey,
            lockDurationInDays = 21,
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private fun bondingCurveToken(supply: Long): Token = MintMetadata(
        address = testMint,
        decimals = 10,
        name = "TestCoin",
        symbol = "TEST",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = dummyKey,
            authority = dummyKey,
            lockDurationInDays = 21,
        ),
        launchpadMetadata = LaunchpadMetadata(
            currencyConfig = dummyKey,
            liquidityPool = dummyKey,
            seed = dummyKey,
            authority = dummyKey,
            mintVault = dummyKey,
            coreMintVault = dummyKey,
            currentCirculatingSupplyQuarks = supply,
            sellFeeBps = 100,
            price = Fiat(fiat = 0.01),
            marketCap = Fiat(fiat = 10000.0),
        ),
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private fun stubVerifiedState(currency: CurrencyCode, mint: Mint, supply: Long) {
        val reserveState = verifiedLaunchpadCurrencyReserveState {
            this.reserveState = launchpadCurrencyReserveState {
                this.mint = solanaAccountId { value = com.google.protobuf.ByteString.copyFrom(mint.bytes.toByteArray()) }
                this.supplyFromBonding = supply
            }
        }

        val rateProto = verifiedCoreMintFiatExchangeRate {
            exchangeRate = coreMintFiatExchangeRate { currencyCode = currency.name }
        }

        every {
            verifiedStateManager.getVerifiedStateFor(currency, mint)
        } returns VerifiedState(rateProto, reserveState)
    }

    // endregion
}
