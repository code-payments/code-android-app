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
                .setTotalAmount(
                    Common.CryptoPaymentAmount.newBuilder()
                        .setQuarks(quarks)
                        .setCurrency("usd")
                        .setNativeAmount(2.5)
                        .setMint(Common.PublicKey.newBuilder().setValue(ByteString.copyFrom(Mint.usdc.bytes.toByteArray())))
                )
                .apply { receiver?.let { setSingleReceiver(Common.UserId.newBuilder().setValue(ByteString.copyFrom(it))) } }
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

        assertEquals(7_654_321L, mapped.asContent().cash.totalAmount.quarks)
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

        assertEquals(listOf<Byte>(9, 8, 7), with.singleReceiver.value.toByteArray().toList())
        assertFalse(without.hasSingleReceiver())
    }

    @Test
    fun `a multi-receiver payment and its payment request id survive the round trip`() {
        val wire = MessagingModel.Content.newBuilder().setCash(
            cash(10L).cash.toBuilder()
                .setMultiReceiver(
                    MessagingModel.CashContent.Payments.newBuilder()
                        .addItems(payment(byteArrayOf(1), 1.25, 4L))
                        .addItems(payment(byteArrayOf(2), 1.25, 6L))
                )
                .setPaymentRequestId(
                    com.codeinc.flipcash.gen.paymentrequest.v1.Model.PaymentRequestId.newBuilder()
                        .setValue(ByteString.copyFrom(ByteArray(16) { it.toByte() }))
                )
        ).build()

        val mapped = wire.toMessageContent() as MessageContent.Cash

        assertNull(mapped.receiver)
        assertEquals(
            listOf(
                MessageContent.Cash.Payment(listOf<Byte>(1), 1.25, 4L),
                MessageContent.Cash.Payment(listOf<Byte>(2), 1.25, 6L),
            ),
            mapped.multiReceiver,
        )
        assertEquals(ByteArray(16) { it.toByte() }.toList(), mapped.paymentRequestId)

        val back = mapped.asContent().cash
        assertEquals(MessagingModel.CashContent.RecipientCase.MULTI_RECEIVER, back.recipientCase)
        assertEquals(2, back.multiReceiver.itemsCount)
        assertEquals(wire.cash.paymentRequestId, back.paymentRequestId)
    }

    private fun payment(receiver: ByteArray, nativeAmount: Double, quarks: Long) =
        MessagingModel.CashContent.Payment.newBuilder()
            .setReceiver(Common.UserId.newBuilder().setValue(ByteString.copyFrom(receiver)))
            .setNativeAmount(nativeAmount)
            .setQuarks(quarks)
            .build()
}
