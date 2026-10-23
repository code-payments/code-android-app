package com.flipcash.app.tokens

import com.flipcash.libs.currency.math.CurveTestInitializer
import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.LaunchpadReserveStateSnapshot
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TokenStateRepricingTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }

        private const val WHOLE_TOKEN = 10_000_000_000L // 10 decimals
        private const val FIVE_DOLLARS = 5_000_000L
    }

    private val mint = Mint(List(32) { 1.toByte() })
    private val dummyKey = PublicKey.fromBase58("11111111111111111111111111111111")

    @Test
    fun `repeated reserve ticks at the same supply leave a bought balance unchanged`() {
        val (quarks, postBuySupply) = buyFiveDollars(preBuySupply = 50_000 * WHOLE_TOKEN)
        val token = launchpadToken(postBuySupply)
        val bought = Fiat.tokenBalance(quarks, token)

        var state = TokenCoordinator.TokenState().withAccounts(
            listOf(TokenWithBalance(token, bought, Fiat.Zero, tokenQuarks = quarks)),
        )
        repeat(5) { state = state.withReserveStates(listOf(snapshot(postBuySupply))) }

        assertEquals(bought, state.balances[mint])
        assertEquals("$5.00", state.balances.getValue(mint).formatted())
    }

    @Test
    fun `a balance fetched at a pre-buy supply is corrected by the next reserve tick`() {
        val preBuySupply = 50_000 * WHOLE_TOKEN
        val (quarks, postBuySupply) = buyFiveDollars(preBuySupply)
        val staleToken = launchpadToken(preBuySupply)
        val staleBalance = Fiat.tokenBalance(quarks, staleToken)

        var state = TokenCoordinator.TokenState().withAccounts(
            listOf(TokenWithBalance(staleToken, staleBalance, Fiat.Zero, tokenQuarks = quarks)),
        )
        state = state.withReserveStates(listOf(snapshot(postBuySupply)))

        val expected = Fiat.tokenBalance(quarks, launchpadToken(postBuySupply))
        assertEquals(expected, state.balances[mint])
        assertEquals(
            postBuySupply,
            state.tokens.getValue(mint).launchpadMetadata?.currentCirculatingSupplyQuarks,
        )
        // Cost basis is held, so the correction shows up as appreciation.
        assertEquals(expected.quarks - staleBalance.quarks, state.appreciation.getValue(mint).quarks)
    }

    @Test
    fun `a stream supply overwritten by a stale fetch is restored by the next delivery`() {
        val preBuySupply = 50_000 * WHOLE_TOKEN
        val (quarks, postBuySupply) = buyFiveDollars(preBuySupply)
        val staleToken = launchpadToken(preBuySupply)

        var state = TokenCoordinator.TokenState().withAccounts(
            listOf(TokenWithBalance(staleToken, Fiat.Zero, Fiat.Zero, tokenQuarks = 0)),
        )
        state = state.withReserveStates(listOf(snapshot(postBuySupply)))
        // The fetch read its metadata before the tick, so it carries the pre-buy supply.
        state = state.withAccounts(
            listOf(
                TokenWithBalance(
                    staleToken,
                    Fiat.tokenBalance(quarks, staleToken),
                    Fiat.Zero,
                    tokenQuarks = quarks,
                ),
            ),
        )
        state = state.withReserveStates(listOf(snapshot(postBuySupply)))

        assertEquals(Fiat.tokenBalance(quarks, launchpadToken(postBuySupply)), state.balances[mint])
        assertEquals(
            postBuySupply,
            state.tokens.getValue(mint).launchpadMetadata?.currentCirculatingSupplyQuarks,
        )
    }

    @Test
    fun `a balance without known quarks keeps its value through a reserve tick`() {
        val token = launchpadToken(50_000 * WHOLE_TOKEN)
        val restored = Fiat(quarks = FIVE_DOLLARS)

        var state = TokenCoordinator.TokenState().withAccounts(
            listOf(TokenWithBalance(token, restored, Fiat.Zero, tokenQuarks = null)),
        )
        state = state.withReserveStates(listOf(snapshot(60_000 * WHOLE_TOKEN)))

        assertEquals(restored, state.balances[mint])
        assertNull(state.holdings.getValue(mint).tokenQuarks)
        assertEquals(
            60_000 * WHOLE_TOKEN,
            state.tokens.getValue(mint).launchpadMetadata?.currentCirculatingSupplyQuarks,
        )
    }

    /** Token quarks a $5 buy from [preBuySupply] yields, and the supply after it. */
    private fun buyFiveDollars(preBuySupply: Long): Pair<Long, Long> {
        val wholeTokens = Estimator.buy(
            amountInQuarks = FIVE_DOLLARS,
            currentSupplyInQuarks = preBuySupply,
            mintDecimals = 10,
            feeBps = 0,
        ).getOrThrow().netTokensToReceive
        val quarks = wholeTokens.movePointRight(10).toLong()
        return quarks to preBuySupply + quarks
    }

    private fun snapshot(supply: Long) = LaunchpadReserveStateSnapshot(
        mint = mint,
        currentSupply = supply,
        timestamp = Instant.fromEpochMilliseconds(0),
    )

    private fun launchpadToken(supply: Long): Token = MintMetadata(
        address = mint,
        decimals = 10,
        name = "TestCoin",
        symbol = "TEST",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(vm = dummyKey, authority = dummyKey, lockDurationInDays = 21),
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
}
