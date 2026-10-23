package com.getcode.opencode

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.internal.network.api.AccountApi
import com.getcode.opencode.internal.network.services.AccountService
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fetches an account's token balances from the live OCP backend on the JVM, with no UI and no DI.
 *
 * The account's recovery phrase comes from the `FLIPCASH_TEST_MNEMONIC` environment variable or the
 * `flipcash.test.mnemonic` entry in `local.properties`. The test is skipped when neither is set, and
 * never prints the phrase or any key.
 */
class HeadlessBalanceTest {

    @Test
    fun fetchesBalancesFromBackend() = runBlocking {
        val words = System.getenv("FLIPCASH_TEST_MNEMONIC")
            ?: System.getProperty("flipcash.test.mnemonic")
        assumeTrue("no test mnemonic configured", !words.isNullOrBlank())

        val mnemonic = requireNotNull(MnemonicPhrase.newInstance(words!!.trim().split(Regex("\\s+")))) {
            "test mnemonic is not a valid phrase"
        }
        val owner = DerivedKey.derive(DerivePath.primary, mnemonic).keyPair

        val channel = OkHttpChannelBuilder.forAddress("ocp-v2.api.flipcash-infra.net", 443)
            .userAgent("OpenCodeProtocol/Desktop/spike")
            .build()
        try {
            val service = AccountService(AccountApi(channel))
            val response = service.getAccounts(owner, owner).getOrThrow()

            assertTrue(response.accounts.isNotEmpty(), "backend returned no token accounts")
            val balances = response.accounts.values
                .groupBy { it.mint }
                .mapValues { (_, infos) -> infos.sumOf { it.balance } }
            println("token accounts: ${response.accounts.size}, balance by mint (quarks): $balances")
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
