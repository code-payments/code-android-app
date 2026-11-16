package com.getcode.opencode.client

import com.getcode.crypt.DerivePath
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.transport.GrpcTransport
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Read calls against the production OCP server through this module's clients: rates over
 * StreamLiveMintData, GetLimits and GetTokenAccountInfos.
 *
 * Skipped unless `OCP_LIVE=1`, and on targets without a live transport (iOS). The transport only
 * lets the read methods through, so SubmitIntent cannot be reached from here. Keys are random per
 * run, except the optional `FLIPCASH_TEST_MNEMONIC` account, whose phrase and keys are never printed.
 */
class LiveReadCallTest {

    @Test
    fun streamLiveMintDataDeliversRates() = live { transport ->
        val rates = withTimeout(30.seconds) {
            CurrencyClient(transport).streamLiveMintData(listOf(Mint.usdf))
                .filterIsInstance<LiveMintDataResponse.ExchangeRates>()
                .first()
        }.rates
        val usd = rates.firstOrNull { it.rate.currency == CurrencyCode.USD }
        println("StreamLiveMintData: ${rates.size} verified rates, USD=${usd?.rate?.fx} at ${usd?.timestamp}")
        assertTrue(rates.isNotEmpty())
    }

    @Test
    fun getLimitsAcceptsOurSignature() = live { transport ->
        val since = Clock.System.now() - 1.days
        val limits = TransactionClient(transport).getLimits(freshKey(), consumedSince = since)
        println("GetLimits, fresh key: USD=${limits.sendLimitFor(CurrencyCode.USD)}, transacted=${limits.amountUsdTransactedSinceConsumption}")
    }

    @Test
    fun getTokenAccountInfosForFreshKeyIsNotFound() = live { transport ->
        val result = AccountInfoClient(transport).getAccountInfos(freshKey())
        println("GetTokenAccountInfos, fresh key: $result")
        assertIs<AccountInfosResult.NotFound>(result)
    }

    @Test
    fun getTokenAccountInfosForTestAccount() = live { transport ->
        val words = liveEnv("FLIPCASH_TEST_MNEMONIC")?.takeIf { it.isNotBlank() } ?: return@live
        val mnemonic = requireNotNull(MnemonicPhrase.newInstance(words.trim().split(Regex("\\s+")))) {
            "FLIPCASH_TEST_MNEMONIC is not a valid phrase"
        }
        val owner = mnemonic.getSolanaKeyPair(DerivePath.primary)
        val result = assertIs<AccountInfosResult.Found>(AccountInfoClient(transport).getAccountInfos(owner))
        println("GetTokenAccountInfos, test account: ${result.accounts.size} token accounts")
        assertTrue(result.accounts.isNotEmpty())
    }

    private fun live(block: suspend (GrpcTransport) -> Unit) = runTest(timeout = 60.seconds) {
        if (liveEnv("OCP_LIVE") != "1") return@runTest
        val transport = liveTransport(OCP_HOST) ?: return@runTest
        try {
            // runTest's virtual clock would fire withTimeout before the network answers.
            withContext(Dispatchers.Default) { block(ReadOnlyTransport(transport)) }
        } finally {
            transport.close()
        }
    }

    private fun freshKey(): Ed25519.KeyPair = Ed25519.createKeyPair(Random.nextBytes(32))

    private companion object {
        const val OCP_HOST = "ocp-v2.api.flipcash-infra.net"
    }
}

/** Passes through only the read methods; anything else, SubmitIntent included, throws. */
private class ReadOnlyTransport(private val delegate: GrpcTransport) : GrpcTransport by delegate {
    private val allowed = setOf(
        CurrencyClient.STREAM_LIVE_MINT_DATA,
        TransactionClient.GET_LIMITS,
        AccountInfoClient.GET_TOKEN_ACCOUNT_INFOS,
    )

    override suspend fun unary(method: String, request: ByteArray): ByteArray =
        delegate.unary(checked(method), request)

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> =
        delegate.bidiStream(checked(method), requests)

    private fun checked(method: String): String {
        check(method in allowed) { "$method is not a read call the live tests may make" }
        return method
    }
}

/** A transport to [host] on targets that can make live calls, or null. */
internal expect fun liveTransport(host: String): GrpcTransport?

internal expect fun liveEnv(name: String): String?
