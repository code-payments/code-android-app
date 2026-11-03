package com.getcode.opencode.controllers

import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class TokenControllerBuildTokenBalancesTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }
    }

    private val controller = TokenController(mockk(), mockk())

    /** A cold cache costs a round trip per mint; those must not queue behind one another. */
    @Test
    fun `metadata lookups run concurrently`() = runTest {
        val provider = SlowProvider(resolves = setOf(Mint.usdf, Mint.usdc))

        controller.buildTokenBalances(
            listOf(account(Mint.usdf), account(Mint.usdc)),
            provider,
        )

        assertEquals(2, provider.maxInFlight)
    }

    @Test
    fun `balances keep the account order and drop an unresolved mint`() = runTest {
        val unresolved = Mint("So11111111111111111111111111111111111111112")
        val provider = SlowProvider(resolves = setOf(Mint.usdf, Mint.usdc))

        val balances = controller.buildTokenBalances(
            listOf(account(Mint.usdc), account(unresolved), account(Mint.usdf)),
            provider,
        )

        assertEquals(listOf(Mint.usdc, Mint.usdf), balances.map { it.token.address })
    }

    private inner class SlowProvider(private val resolves: Set<Mint>) : TokenMetadataProvider {
        private var inFlight = 0
        var maxInFlight = 0
            private set

        override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(100)
            inFlight--
            return if (mint in resolves) {
                Result.success(TokenResult(token(mint), DataSource.Network))
            } else {
                Result.failure(IllegalStateException("no metadata for $mint"))
            }
        }

        override fun observeTokenCache(): Flow<Map<Mint, Token>> = emptyFlow()
    }

    private fun account(mint: Mint): AccountInfo = mockk {
        every { this@mockk.mint } returns mint
        every { balance } returns 1_000_000
        every { usdCostBasis } returns 0.0
    }

    private val dummyKey = PublicKey.fromBase58("11111111111111111111111111111111")

    private fun token(mint: Mint): Token = MintMetadata(
        address = mint,
        decimals = 6,
        name = "",
        symbol = "",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(vm = dummyKey, authority = dummyKey, lockDurationInDays = 21),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )
}
