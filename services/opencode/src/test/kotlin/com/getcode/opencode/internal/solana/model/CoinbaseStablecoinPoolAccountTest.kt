package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.base58
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoinbaseStablecoinPoolAccountTest {

    /**
     * Discriminator through fee_recipient (offsets 0..168) of the live pool account
     * `CrDL9SoCyW1tBgn8k7rgGSpWhnszneWDbvKvqPAU4PL9`, captured from mainnet. Real bytes,
     * not a synthetic layout built to match the parser's assumption — a synthetic vector
     * cannot catch an offset that never matched the deployed struct.
     */
    private val realPoolAccountDataBase64 =
        "QiYRQLxQRIEFHqE9vluQFKO1wbEwnd22aRe9qGrV03SIsz1AV7GqT/yEzcR/f+ALaKG4KMxbBfZ5dTNFPqxxZHMfbTqNfj6St0p" +
            "++yObz2IILMcKsoko07O+oRSDek7YwzrH9TroL1+QbHdT/t9qpcq4Kyx8OPWZm79AIUM9UlN+X6ujF0hPgzT4z8SXtrVTfBhZj7Lz" +
            "SPTgCpHoi6cjfPqXvflOJvRB"

    private fun realPoolAccountData(): ByteArray = Base64.getDecoder().decode(realPoolAccountDataBase64)

    @Test
    fun `parses fee_recipient at the deployed offset 136 from real captured account data`() {
        val account = CoinbaseStablecoinPoolAccount.fromAccountData(realPoolAccountData())

        assertEquals(
            "4ZnFXk7KyB5khDqjWSHqHBQH1nQCnmvkr1pRFivWcP7e",
            account.feeRecipient.base58(),
        )
    }

    @Test
    fun `rejects data shorter than the required bounds`() {
        val truncated = realPoolAccountData().copyOf(100)

        assertFailsWith<IllegalArgumentException> {
            CoinbaseStablecoinPoolAccount.fromAccountData(truncated)
        }
    }

    @Test
    fun `rejects a wrong account discriminator`() {
        val corrupted = realPoolAccountData().copyOf()
        corrupted[0] = corrupted[0].inc()

        assertFailsWith<IllegalArgumentException> {
            CoinbaseStablecoinPoolAccount.fromAccountData(corrupted)
        }
    }
}
