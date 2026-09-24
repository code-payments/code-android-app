package com.flipcash.app.messenger.internal

import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class RuleTokenTest {

    private val mint = Mint(List(32) { 1.toByte() })
    private val dummyKey = PublicKey.fromBase58("11111111111111111111111111111111")

    /** Mirrors TokenCoordinator: a network fetch of an unheld mint is not written to the cache. */
    private class FakeProvider(private val network: Token?) : TokenMetadataProvider {
        val cache = MutableStateFlow<Map<Mint, Token>>(emptyMap())

        override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> =
            cache.value[mint]?.let { Result.success(TokenResult(it, DataSource.Memory)) }
                ?: network?.let { Result.success(TokenResult(it, DataSource.Network)) }
                ?: Result.failure(IllegalStateException("not found"))

        override fun observeTokenCache(): Flow<Map<Mint, Token>> = cache
    }

    @Test
    fun `an unheld rule token resolves from the network`() = runTest {
        val provider = FakeProvider(network = token(name = "OPOS"))

        assertEquals("OPOS", provider.observeRuleToken(mint).first().name)
    }

    @Test
    fun `a copy hydrated into the cache replaces the fetched one`() = runTest {
        val provider = FakeProvider(network = token(name = "OPOS"))

        val names = mutableListOf<String>()
        val job = launch { provider.observeRuleToken(mint).take(2).toList().mapTo(names) { it.name } }
        testScheduler.runCurrent()
        provider.cache.value = mapOf(mint to token(name = "OPOS after buy"))
        job.join()

        assertEquals(listOf("OPOS", "OPOS after buy"), names)
    }

    @Test
    fun `a failed fetch still resolves once the cache fills`() = runTest {
        val provider = FakeProvider(network = null)

        val job = launch { assertEquals("OPOS", provider.observeRuleToken(mint).first().name) }
        testScheduler.runCurrent()
        provider.cache.value = mapOf(mint to token(name = "OPOS"))
        job.join()
    }

    private fun token(name: String): Token = MintMetadata(
        address = mint,
        decimals = 10,
        name = name,
        symbol = "OPOS",
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
            currentCirculatingSupplyQuarks = 0,
            sellFeeBps = 100,
            price = Fiat(fiat = 0.01),
            marketCap = Fiat(fiat = 10000.0),
        ),
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )
}
