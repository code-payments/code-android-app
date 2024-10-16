package com.getcode.solana.encoding

import com.getcode.solana.AgoraMemo
import com.getcode.solana.MagicByte.Companion.default
import com.getcode.solana.TransferType
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.kin.sdk.base.tools.toByteArray
import java.util.*

class AgoraMemo_Test {
    @Test
    fun testEncodingSpecificData() {
        val memo = AgoraMemo(
            magicByte = default,
            version = 2,
            transferType = TransferType.p2p,
            appIndex = 10,
            bytes = listOf(0xAE.toByte(), 0xFD.toByte())
        )
        val decoded = AgoraMemo.newInstance(memo.encode().toList())
        assertEquals(memo.bytes, decoded.bytes)
    }

    @Test
    fun testEncodingValidDataLessThanMax() {
        val memo = AgoraMemo(
            magicByte = default,
            version = 2,
            transferType = TransferType.p2p,
            appIndex = 10,
            bytes = UUID.randomUUID().toByteArray().toList()
        )
        val decoded = AgoraMemo.newInstance(memo.encode().toList())
        assertEquals(memo.bytes, decoded.bytes)
    }


    @Test
    fun testEncodingValidDataLargerThanMax() {
        val memo = AgoraMemo(
            magicByte = default,
            version = 2,
            transferType = TransferType.p2p,
            appIndex = 10,
            bytes = UUID.randomUUID().toByteArray().toList() + UUID.randomUUID().toByteArray()
                .toList()
        )
        val decoded = AgoraMemo.newInstance(memo.encode().toList())
        assertEquals(memo.bytes, decoded.bytes.subList(0, 28))
    }


    @Test
    fun testEncodingappIndexValidRange() {
        val appIndexes = listOf(0, 10_000, 65_535)
        val foreignKeyBytes =
            UUID.randomUUID().toByteArray().toList() + UUID.randomUUID().toByteArray().toList()

        appIndexes.forEach { appIndex ->
            val memo = AgoraMemo(
                magicByte = default,
                version = 7,
                transferType = TransferType.p2p,
                appIndex = appIndex,
                bytes = foreignKeyBytes
            )
            val decoded = AgoraMemo.newInstance(memo.encode().toList())
            assertEquals(memo.bytes, decoded.bytes.subList(0, 28))

            assertEquals(decoded.magicByte, default)
            assertEquals(decoded.version, 7)
            assertEquals(decoded.transferType, TransferType.p2p)
            assertEquals(decoded.appIndex, appIndex)
            assertEquals(decoded.bytes, memo.bytes)
        }
    }
}