package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.base58
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalEncodingApi::class)
class CoinbaseStablecoinPoolAccountTest {

    // First 315 bytes of mainnet pool CrDL9SoCyW1tBgn8k7rgGSpWhnszneWDbvKvqPAU4PL9
    // at slot ~445672210, after the 2026-09-08 MigrateAuthorities instruction. The
    // live account is 2107 bytes with the remainder zeroed. Same fixture as ocp-server.
    private val mainnetPrefix = "QiYRQLxQRIEFHqE9vluQFKO1wbEwnd22aRe9qGrV03SIsz1AV7GqT/yEzcR/f+ALaKG4KMxbBfZ5dTNFPqxxZHMfbTqNfj6St0p+" +
        "+yObz2IILMcKsoko07O+oRSDek7YwzrH9TroL1+QbHdT/t9qpcq4Kyx8OPWZm79AIUM9UlN+X6ujF0hPgzT4z8SXtrVTfBhZj7Lz" +
        "SPTgCpHoi6cjfPqXvflOJvRBAgAAAN0H70q0C5DeChX575Umuo4KwnYx+lqZmPTGnq1wMK2QSoyv1lJlvQkMgeq0VkN3NML2MHaz" +
        "cTzSusODf5c6RhACAAAAxvp6877brTo9ZfNqq8l0MbG75MLS9uDkfKYCA0UvXWE908SAij1Ps5+uycukm1pjSlLt4RVI9SSqIPLN" +
        "Ru+AcQAAAAAAAAAAAAD/"

    private fun mainnetData(): ByteArray {
        val prefix = Base64.decode(mainnetPrefix)
        assertEquals(315, prefix.size)
        return prefix.copyOf(2107)
    }

    @Test
    fun `parses mainnet pool account after authority migration`() {
        val pool = CoinbaseStablecoinPoolAccount.fromAccountData(mainnetData())

        assertEquals("Lz8QXHjETKQnt1fzsKbN4AyQEhhVAFB2YKwAMqksr7G", pool.pauseAuthority.base58())
        assertEquals("HzjC9U1WifkqLhYMx592UG1fJqX3BtRjCqPPPTo3hA8R", pool.unpauseAuthority.base58())
        assertEquals("DLVVcd3xfwWeCwGz1EUQbqaNC88NooN6s9ifWo87QZst", pool.treasuryAuthority.base58())
        assertEquals("Aimdv5hcHfm2PKuGwDW9H81iibZoYKLv3TPMEaZhmvqG", pool.configureAuthority.base58())
        assertEquals("4ZnFXk7KyB5khDqjWSHqHBQH1nQCnmvkr1pRFivWcP7e", pool.feeRecipient.base58())
    }

    @Test
    fun `fee recipient is read at the post-migration offset`() {
        // Pre-migration layout put fee_recipient at byte 72; reading there now yields
        // treasury_authority, which would send pool fees to the wrong account.
        val pool = CoinbaseStablecoinPoolAccount.fromAccountData(mainnetData())
        assertEquals(pool.treasuryAuthority, keyAt(mainnetData(), 72))
        assertEquals(pool.feeRecipient, keyAt(mainnetData(), 136))
    }

    @Test
    fun `rejects truncated data`() {
        val data = mainnetData()
        for (size in listOf(0, 8, 135, 167)) {
            assertFailsWith<IllegalArgumentException>("size $size") {
                CoinbaseStablecoinPoolAccount.fromAccountData(data.copyOf(size))
            }
        }
        // Exactly the fixed prefix is enough.
        CoinbaseStablecoinPoolAccount.fromAccountData(data.copyOf(168))
    }

    @Test
    fun `rejects wrong discriminator`() {
        val data = mainnetData()
        // TokenVault discriminator: [121, 7, 84, 254, 151, 228, 43, 144]
        byteArrayOf(121, 7, 84, 254.toByte(), 151.toByte(), 228.toByte(), 43, 144.toByte())
            .copyInto(data)
        assertFailsWith<IllegalArgumentException> {
            CoinbaseStablecoinPoolAccount.fromAccountData(data)
        }
    }

    private fun keyAt(data: ByteArray, offset: Int) =
        com.getcode.solana.keys.PublicKey(data.sliceArray(offset until offset + 32).toList())
}
