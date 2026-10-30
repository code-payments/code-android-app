package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.core.feed.SwapState
import com.flipcash.app.core.feed.SwappedCryptoMetadata
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A convert spans two mints and belongs on both token histories. Mirrors the SQL rule in
 * `MessageDao.observeRecentForMint`.
 */
class MintInvolvementTest {

    private val source = Mint.usdf
    private val destination = Mint.usdc
    private val unrelated = Mint("So11111111111111111111111111111111111111112")

    private fun localFiat(mint: Mint) = LocalFiat(
        usdf = Fiat(20.0, CurrencyCode.USD),
        nativeAmount = Fiat(20.0, CurrencyCode.USD),
        mint = mint,
    )

    private fun swap(from: Mint, to: Mint) = SwappedCryptoMetadata(
        from = localFiat(from),
        toMint = PublicKey(to.bytes),
        toAmount = localFiat(to),
        fee = Fiat(0.25, CurrencyCode.USD),
        swapState = SwapState.SUCCEEDED,
    )

    private fun message(metadata: MessageMetadata?, mint: Mint) = ActivityFeedMessage(
        id = listOf(0x01, 0x02, 0x03).map { it.toByte() },
        text = "Converted",
        amount = localFiat(mint),
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = MessageState.COMPLETED,
        metadata = metadata,
    )

    @Test
    fun `a convert belongs to the source token`() {
        val msg = message(MessageMetadata.SwappedCrypto(swap(source, destination)), mint = source)
        assertTrue(msg.involves(source))
    }

    @Test
    fun `a convert belongs to the destination token`() {
        val msg = message(MessageMetadata.SwappedCrypto(swap(source, destination)), mint = source)
        assertTrue(msg.involves(destination))
    }

    @Test
    fun `a convert does not belong to an unrelated token`() {
        val msg = message(MessageMetadata.SwappedCrypto(swap(source, destination)), mint = source)
        assertFalse(msg.involves(unrelated))
    }

    @Test
    fun `a plain entry belongs only to its own token`() {
        val msg = message(MessageMetadata.DepositedCrypto, mint = source)
        assertTrue(msg.involves(source))
        assertFalse(msg.involves(destination))
    }

    @Test
    fun `a withdrawal executed as a swap belongs only to the source token`() {
        val msg = message(
            MessageMetadata.WithdrewCrypto(swapMetadata = swap(source, destination)),
            mint = source,
        )
        assertTrue(msg.involves(source))
        assertFalse(msg.involves(destination))
    }
}
