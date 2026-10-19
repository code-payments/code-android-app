package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.MessageContent
import com.getcode.solana.keys.Mint
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CashContentMappingTest {

    private fun cash(quarks: Long, receiver: ByteArray? = null) = MessagingModel.Content.newBuilder()
        .setCash(
            MessagingModel.CashContent.newBuilder()
                .setIntentId(Common.IntentId.newBuilder().setValue(ByteString.copyFrom(byteArrayOf(1, 2, 3))))
                .setAmount(
                    Common.CryptoPaymentAmount.newBuilder()
                        .setQuarks(quarks)
                        .setCurrency("usd")
                        .setNativeAmount(2.5)
                        .setMint(Common.PublicKey.newBuilder().setValue(ByteString.copyFrom(Mint.usdc.bytes.toByteArray())))
                )
                .apply { receiver?.let { setReceiver(Common.UserId.newBuilder().setValue(ByteString.copyFrom(it))) } }
        )
        .build()

    @Test
    fun `cash keeps the on-chain token quarks apart from the fiat amount`() {
        val mapped = cash(7_654_321L).toMessageContent() as MessageContent.Cash

        assertEquals(7_654_321L, mapped.tokenQuarks)
        assertEquals(2_500_000L, mapped.amount.quarks)
    }

    @Test
    fun `token quarks are written back to the wire when known`() {
        val mapped = cash(7_654_321L).toMessageContent()

        assertEquals(7_654_321L, mapped.asContent().cash.amount.quarks)
    }

    @Test
    fun `the receiver is parsed when present and null when absent`() {
        val with = cash(1L, receiver = byteArrayOf(9, 8, 7)).toMessageContent() as MessageContent.Cash
        val without = cash(1L).toMessageContent() as MessageContent.Cash

        assertEquals(listOf<Byte>(9, 8, 7), with.receiver)
        assertNull(without.receiver)
    }

    @Test
    fun `the receiver is written back to the wire only when known`() {
        val with = cash(1L, receiver = byteArrayOf(9, 8, 7)).toMessageContent().asContent().cash
        val without = cash(1L).toMessageContent().asContent().cash

        assertEquals(listOf<Byte>(9, 8, 7), with.receiver.value.toByteArray().toList())
        assertFalse(without.hasReceiver())
    }
}
