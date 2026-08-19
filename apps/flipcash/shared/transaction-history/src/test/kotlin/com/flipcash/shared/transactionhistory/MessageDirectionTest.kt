package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.shared.transactionhistory.internal.isOutgoing
import com.getcode.solana.keys.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDirectionTest {

    @Test
    fun `sent variants are outgoing`() {
        assertEquals(true, MessageMetadata.DirectlySentCrypto(phoneNumber = null).isOutgoing)
        assertEquals(true, MessageMetadata.IndirectlySentCrypto(PublicKey(ByteArray(32).toList()), canCancel = true).isOutgoing)
        assertEquals(true, MessageMetadata.WithdrewCrypto().isOutgoing)
        assertEquals(true, MessageMetadata.SoldToken.isOutgoing)
        assertEquals(true, MessageMetadata.PaidCrypto(poolId = listOf()).isOutgoing)
    }

    @Test
    fun `received variants are incoming`() {
        assertEquals(false, MessageMetadata.ReceivedCrypto(phoneNumber = null).isOutgoing)
        assertEquals(false, MessageMetadata.DepositedCrypto.isOutgoing)
        assertEquals(false, MessageMetadata.BoughtToken.isOutgoing)
    }

    @Test
    fun `unknown is not outgoing`() {
        assertEquals(false, MessageMetadata.Unknown.isOutgoing)
    }
}
