package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the bytes an external-wallet USDC deposit puts on the wire, starting from the real pool
 * account rather than a hand-written fee recipient.
 *
 * The chain under test is the one the Phantom deep link runs: parse the pool account, take
 * `fee_recipient` out of it, build the USDC -> USDF swap, encode. A wrong offset yields a
 * valid-looking [PublicKey] and a transaction that encodes fine, so only the end-to-end bytes
 * catch it.
 *
 * The fixture below was accepted by `simulateTransaction` on mainnet (no error, 64422 compute
 * units, `Swapped 5000000 tokens`). Two earlier shapes of this same transaction were rejected
 * there: `fee_recipient` read at the offset the published IDL implies fails with
 * `ConstraintAddress` (2012), and `minAmountOut = 0` fails with `InvalidAmount` (6002).
 */
class CoinbaseStablecoinDepositTransactionTest {

    /**
     * Discriminator through fee_recipient (offsets 0..168) of the live pool account
     * `CrDL9SoCyW1tBgn8k7rgGSpWhnszneWDbvKvqPAU4PL9`, captured from mainnet.
     */
    private val poolAccountBase64 =
        "QiYRQLxQRIEFHqE9vluQFKO1wbEwnd22aRe9qGrV03SIsz1AV7GqT/yEzcR/f+ALaKG4KMxbBfZ5dTNFPqxxZHMfbTqNfj6St0p+" +
            "+yObz2IILMcKsoko07O+oRSDek7YwzrH9TroL1+QbHdT/t9qpcq4Kyx8OPWZm79AIUM9UlN+X6ujF0hPgzT4z8SXtrVTfBhZj7Lz" +
            "SPTgCpHoi6cjfPqXvflOJvRB"

    /**
     * A mainnet wallet with a USDC balance, so these bytes can be replayed through
     * `simulateTransaction` unchanged when the pool or the program moves.
     */
    private val sender = PublicKey.fromBase58("13V7ou4zHHwDVaAGWxqHSwU2sVzRR4m62XWqCFxhA5fD")
    private val owner = PublicKey(ByteArray(32) { 7 }.toList())
    private val swapId = SwapId(PublicKey(ByteArray(32) { 9 }.toList()))
    private val blockhash = Hash(ByteArray(32) { 99 }.toList())
    private val amount = 5_000_000L

    /** Any change here is a wire-format change on the deposit path. Re-simulate before updating. */
    private val expectedTransactionBase64 =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAQAPFgCi9Dy6" +
            "TYs7wKM+kEYccLhPtMSNYE1wUvMQtohQE/WACCBGwXmMQDQ0qkChD8P24pvvO8cn53TnEdWfDF4qcEUITd86ORI7wd27l38gKTT3" +
            "ojsmucS0ZlOGpp5sm3vBtGbwtkF5L6Cvq8w77bPnPNPBRTgo/TPhsNVWk8JRRULgiBrs5GFDW1LEB5bkqApFskZwilGMY+qfcCAS" +
            "Kl0CEqDasAlxgEeKAYkvEA0wv4eaENRzq1p4QtFatVIft1mOcv1BxHqEPYi++WSNjiQnW4G/HLlzdds46oPbue+ighXzAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGp9UXGSxcUSGMyUw9SvF/WNruCJuh/UTj29mKAAAAAA+/7dMeCXcpTlNjHO5mhTiv" +
            "EMVIGVamVD65t7F9VMsYF6x59+V9mYRAdEA3nhzuBFUPeM3TLK71ctavlwzvl5wrihFyRC8DGIR3MAeAqjMI/sw+jMJK5RKyf9C3" +
            "uhVGKjT4z8SXtrVTfBhZj7LzSPTgCpHoi6cjfPqXvflOJvRBPdPEgIo9T7OfrsnLpJtaY0pS7eEVSPUkqiDyzUbvgHGVur3fbwX8" +
            "hBrwhMiwez2F9VqDBFKemuE5F9sZF3qiyLAL9R/5yXsykNTLL2XYLZNgA2ZHIRl2NJrr0fTq8UW2xvp6877brTo9ZfNqq8l0MbG7" +
            "5MLS9uDkfKYCA0UvXWEDBkZv5SEXMv/srbpyw5vnvIzlu8X3EmssQ5s6QAAAAAVKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL0" +
            "7vagBt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKkMQVks6uA/heUvYKn86iKm18aeqHA3x3bBSlnDQ9z8RIyXJY9OJInx" +
            "uz0QKRSODYMLWhOZ2v8QhASOe9jb6fhZY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2MIEQAFAkANAwARAAkD6AMAAAAA" +
            "AAAVBwAEAA0HEwgBARUHAAUODQcTCAEBFQcAAwAQBxMIAQESACtjR2ZIaUM2S2dnM0ZwRlp2Z3dHY3N3c0NSdHA0YUJQMmZ6dVhS" +
            "UVBpenVOFBAPCgsBAgMEBgwQDQAJExUHGPjGnpHhdYfIQEtMAAAAAABAS0wAAAAAABMDBAUACQNAS0wAAAAAAAA="

    private fun pool(): FundSwapPool.CoinbaseStableSwapper =
        FundSwapPool.CoinbaseStableSwapper.fromAccountData(
            Base64.getDecoder().decode(poolAccountBase64)
        )

    @Test
    fun `the real pool account yields the fee recipient the program enforces`() {
        assertEquals(
            "4ZnFXk7KyB5khDqjWSHqHBQH1nQCnmvkr1pRFivWcP7e",
            pool().feeRecipient.base58(),
        )
    }

    @Test
    fun `pool bytes through to encoded transaction produce the simulated byte sequence`() {
        val transaction = TransactionBuilder.usdcFundSwap(
            owner = owner,
            sender = sender,
            amount = amount,
            pool = pool(),
            swapId = swapId,
            blockhash = blockhash,
        )

        assertEquals(
            expectedTransactionBase64,
            Base64.getEncoder().encodeToString(transaction.encode().toByteArray()),
        )
    }

    @Test
    fun `the encoded transaction fits in a single packet`() {
        assertTrue(Base64.getDecoder().decode(expectedTransactionBase64).size <= 1232)
    }
}
