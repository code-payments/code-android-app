package com.getcode.opencode.controllers

import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenControllerFetchTokenBalancesTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }
    }

    private val cluster: AccountCluster = mockk()
    private val accounts: AccountController = mockk()
    private val currency: CurrencyController = mockk()
    private val controller = TokenController(accounts, currency)

    /** The regression: an owner with many tokens paid one metadata round trip per token. */
    @Test
    fun `metadata for every mint comes from one batched call`() = runTest {
        givenAccounts(account(Mint.usdf, 1_000_000), account(Mint.usdc, 2_000_000))
        coEvery { currency.getMintMetadata(any()) } returns
            Result.success(listOf(token(Mint.usdf), token(Mint.usdc)))

        val balances = controller.fetchTokenBalances(cluster).getOrThrow()

        assertEquals(listOf(Mint.usdf, Mint.usdc), balances.map { it.token.address })
        coVerify(exactly = 1) { currency.getMintMetadata(any()) }
        coVerify { currency.getMintMetadata(match { it.toSet() == setOf(Mint.usdf, Mint.usdc) }) }
    }

    @Test
    fun `an owner with no accounts has no balances, and costs no metadata call`() = runTest {
        givenAccounts()

        assertEquals(emptyList(), controller.fetchTokenBalances(cluster).getOrThrow())
        coVerify(exactly = 0) { currency.getMintMetadata(any()) }
    }

    /** A dropped token would make the total silently short, so a missing mint fails the fetch. */
    @Test
    fun `a mint the batch does not return fails the fetch`() = runTest {
        givenAccounts(account(Mint.usdf, 1_000_000), account(Mint.usdc, 2_000_000))
        coEvery { currency.getMintMetadata(any()) } returns Result.success(listOf(token(Mint.usdf)))

        assertTrue(controller.fetchTokenBalances(cluster).isFailure)
    }

    @Test
    fun `a failed metadata batch fails the fetch`() = runTest {
        givenAccounts(account(Mint.usdf, 1_000_000))
        coEvery { currency.getMintMetadata(any()) } returns Result.failure(IllegalStateException())

        assertTrue(controller.fetchTokenBalances(cluster).isFailure)
    }

    private fun givenAccounts(vararg infos: AccountInfo) {
        coEvery { accounts.getAccounts(cluster, cluster, any()) } returns Result.success(
            AccountResponse(infos.associateBy { mockk<PublicKey>() })
        )
    }

    private fun account(mint: Mint, quarks: Long): AccountInfo = mockk {
        every { this@mockk.mint } returns mint
        every { balance } returns quarks
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
