package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.model.MessageAddressLookupTable
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionedMessageV0Test {

    private fun publicKey(seed: Int): PublicKey {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return PublicKey(bytes.toList())
    }

    private fun hash(seed: Int): Hash {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return Hash(bytes.toList())
    }

    // Note: decode (newInstance) tests are excluded because trace() calls Firebase Crashlytics
    // which is not initialized in JVM unit tests.

    @Test
    fun encodeContainsVersionByte() {
        val msg = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 1),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )
        val encoded = msg.encode()
        // First byte should be version 0 + offset (0x80)
        assertEquals(0x80.toByte(), encoded.first())
    }

    @Test
    fun encodeHeaderFollowsVersion() {
        val msg = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 2, readOnlySigners = 1, readOnly = 3),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )
        val encoded = msg.encode()
        // Header starts at byte 1
        assertEquals(2.toByte(), encoded[1]) // requiredSignatures
        assertEquals(1.toByte(), encoded[2]) // readOnlySigners
        assertEquals(3.toByte(), encoded[3]) // readOnly
    }

    @Test
    fun encodingIsDeterministic() {
        val msg = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 1),
            staticAccountKeys = listOf(publicKey(1), publicKey(2)),
            recentBlockhash = hash(5),
            instructions = listOf(CompiledInstruction(1, listOf(0), listOf(0xFF.toByte()))),
            addressLookupTables = emptyList(),
        )

        assertEquals(msg.encode(), msg.encode())
    }

    @Test
    fun encodeWithInstructionsProducesLargerOutput() {
        val noInstr = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )
        val withInstr = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = listOf(CompiledInstruction(0, listOf(0), listOf(0x01, 0x02))),
            addressLookupTables = emptyList(),
        )

        assertTrue(withInstr.encode().size > noInstr.encode().size)
    }

    @Test
    fun encodeWithALTsProducesLargerOutput() {
        val noALT = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )
        val withALT = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = listOf(
                MessageAddressLookupTable(publicKey(50), listOf(0, 1), listOf(2))
            ),
        )

        assertTrue(withALT.encode().size > noALT.encode().size)
    }

    @Test
    fun encodeMultipleStaticKeysIncreaseSize() {
        val oneKey = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )
        val threeKeys = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1), publicKey(2), publicKey(3)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = emptyList(),
        )

        // Each additional key adds 32 bytes
        assertEquals(oneKey.encode().size + 64, threeKeys.encode().size)
    }

    @Test
    fun description() {
        val msg = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 0),
            staticAccountKeys = listOf(publicKey(1), publicKey(2)),
            recentBlockhash = hash(1),
            instructions = emptyList(),
            addressLookupTables = listOf(
                MessageAddressLookupTable(publicKey(50), listOf(0), listOf(1))
            ),
        )

        assertTrue(msg.description.contains("V0"))
        assertTrue(msg.description.contains("staticKeys: 2"))
        assertTrue(msg.description.contains("lookups: 1"))
    }
}
