package com.getcode.solana

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MessageTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    private fun testHash(): Hash =
        Hash(ByteArray(32) { 0xAB.toByte() }.toList())

    // --- newInstance from accounts ---

    @Test
    fun constructsHeaderFromAccounts() {
        val payer = AccountMeta.payer(testKey(1))
        val writable = AccountMeta.writable(testKey(2))
        val readonly = AccountMeta.readonly(testKey(3))
        val program = AccountMeta(testKey(4), isSigner = false, isWritable = false, isPayer = false, isProgram = true)

        val message = Message.newInstance(
            accounts = listOf(payer, writable, readonly, program),
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        // payer is the only signer
        assertEquals(1, message.header.requiredSignatures)
    }

    @Test
    fun payerIsFirstAccount() {
        val payer = AccountMeta.payer(testKey(1))
        val writable = AccountMeta.writable(testKey(2))

        val message = Message.newInstance(
            accounts = listOf(writable, payer),
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        assertEquals(testKey(1), message.accounts.first().publicKey)
    }

    // --- encode / decode roundtrip ---

    @Test
    fun encodeDecodeRoundtripNoInstructions() {
        val payer = AccountMeta.payer(testKey(1))

        val original = Message.newInstance(
            accounts = listOf(payer),
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val encoded = original.encode()
        val decoded = Message.newInstance(encoded.toList())

        assertNotNull(decoded)
        assertEquals(original.header, decoded.header)
        assertEquals(original.recentBlockhash, decoded.recentBlockhash)
        assertEquals(original.accounts.size, decoded.accounts.size)
        assertEquals(original.instructions.size, decoded.instructions.size)
    }

    @Test
    fun encodeDecodeRoundtripWithInstruction() {
        val payer = AccountMeta.payer(testKey(1))
        val dest = AccountMeta.writable(testKey(2))
        val program = AccountMeta(testKey(3), isSigner = false, isWritable = false, isPayer = false, isProgram = true)

        val instruction = Instruction(
            program = testKey(3),
            accounts = listOf(payer, dest),
            data = listOf(0x01, 0x02, 0x03)
        )

        val original = Message.newInstance(
            accounts = listOf(payer, dest, program),
            recentBlockhash = testHash(),
            instructions = listOf(instruction)
        )

        val encoded = original.encode()
        val decoded = Message.newInstance(encoded.toList())

        assertNotNull(decoded)
        assertEquals(original.header, decoded.header)
        assertEquals(1, decoded.instructions.size)
        assertEquals(listOf<Byte>(0x01, 0x02, 0x03), decoded.instructions[0].data)
    }

    @Test
    fun encodeDecodePreservesBlockhash() {
        val hash = testHash()
        val payer = AccountMeta.payer(testKey(1))

        val original = Message.newInstance(
            accounts = listOf(payer),
            recentBlockhash = hash,
            instructions = emptyList()
        )

        val decoded = Message.newInstance(original.encode().toList())
        assertNotNull(decoded)
        assertEquals(hash, decoded.recentBlockhash)
    }

    // --- account sorting ---

    @Test
    fun signersBeforeNonSigners() {
        val payer = AccountMeta.payer(testKey(1))
        val signer = AccountMeta.writable(testKey(2), signer = true)
        val nonsigner = AccountMeta.writable(testKey(3))

        val message = Message.newInstance(
            accounts = listOf(nonsigner, signer, payer),
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        // First two should be signers
        assert(message.accounts[0].isSigner)
        assert(message.accounts[1].isSigner)
        assert(!message.accounts[2].isSigner)
    }

    @Test
    fun writableBeforeReadonly() {
        val payer = AccountMeta.payer(testKey(1))
        val readonly = AccountMeta.readonly(testKey(2))
        val writable = AccountMeta.writable(testKey(3))

        val message = Message.newInstance(
            accounts = listOf(readonly, writable, payer),
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        // Payer first (writable signer), then writable non-signer, then readonly
        assert(message.accounts[0].isWritable)
    }

    @Test
    fun multipleInstructionsRoundtrip() {
        val payer = AccountMeta.payer(testKey(1))
        val acc = AccountMeta.writable(testKey(2))
        val program = AccountMeta(testKey(3), isSigner = false, isWritable = false, isPayer = false, isProgram = true)

        val ix1 = Instruction(
            program = testKey(3),
            accounts = listOf(payer),
            data = listOf(0x01)
        )
        val ix2 = Instruction(
            program = testKey(3),
            accounts = listOf(acc),
            data = listOf(0x02, 0x03)
        )

        val original = Message.newInstance(
            accounts = listOf(payer, acc, program),
            recentBlockhash = testHash(),
            instructions = listOf(ix1, ix2)
        )

        val decoded = Message.newInstance(original.encode().toList())
        assertNotNull(decoded)
        assertEquals(2, decoded.instructions.size)
        assertEquals(listOf<Byte>(0x01), decoded.instructions[0].data)
        assertEquals(listOf<Byte>(0x02, 0x03), decoded.instructions[1].data)
    }
}
