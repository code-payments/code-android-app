package com.getcode.solana

import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SolanaTransactionTest {

    private fun testHash(): Hash =
        Hash(ByteArray(32) { 0xAB.toByte() }.toList())

    // --- Construction ---

    @Test
    fun newInstanceCreatesTransactionWithPlaceholderSignatures() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        assertEquals(1, tx.signatures.size)
        assertEquals(Signature.zero, tx.signatures[0])
    }

    @Test
    fun newInstanceWithInstructionIncludesProgram() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())
        val program = PublicKey(ByteArray(32) { 2 }.toList())

        val instruction = Instruction(
            program = program,
            accounts = emptyList(),
            data = listOf(0x01)
        )

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = listOf(instruction)
        )

        assertTrue(tx.message.accounts.any { it.publicKey == program })
    }

    // --- Signing ---

    @Test
    fun signProducesValidSignature() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val signatures = tx.sign(keyPair)
        assertEquals(1, signatures.size)
        assertEquals(64, signatures[0].bytes.size)
        // Signature should not be all zeros
        assertTrue(signatures[0] != Signature.zero)
    }

    @Test
    fun signatureIsVerifiable() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val signatures = tx.sign(keyPair)
        val messageData = tx.message.encode()

        assertTrue(
            Ed25519.verify(
                signatures[0].bytes.toByteArray(),
                messageData,
                keyPair.publicKeyBytes
            )
        )
    }

    @Test
    fun signIsDeterministic() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx1 = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val tx2 = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val sig1 = tx1.sign(keyPair)
        val sig2 = tx2.sign(keyPair)
        assertEquals(sig1[0].bytes, sig2[0].bytes)
    }

    @Test
    fun signRejectsKeyNotInAccountList() {
        val seed1 = ByteArray(32) { 1 }
        val seed2 = ByteArray(32) { 2 }
        val payerKp = Ed25519.createKeyPair(seed1)
        val otherKp = Ed25519.createKeyPair(seed2)
        val payer = PublicKey(payerKp.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        assertFailsWith<Exception> {
            tx.sign(otherKp)
        }
    }

    @Test
    fun signRejectsTooManySigners() {
        val seed1 = ByteArray(32) { 1 }
        val seed2 = ByteArray(32) { 2 }
        val kp1 = Ed25519.createKeyPair(seed1)
        val kp2 = Ed25519.createKeyPair(seed2)
        val payer = PublicKey(kp1.publicKeyBytes.toList())

        // Transaction with only 1 required signature (payer)
        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        assertFailsWith<Exception> {
            tx.sign(kp1, kp2)
        }
    }

    // --- Encode / Decode roundtrip ---

    @Test
    fun encodeDecodeRoundtrip() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val encoded = tx.encode()
        val decoded = SolanaTransaction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(tx.message.header, decoded.message.header)
        assertEquals(tx.message.recentBlockhash, decoded.message.recentBlockhash)
        assertEquals(tx.signatures.size, decoded.signatures.size)
    }

    @Test
    fun encodeDecodeWithSignatureRoundtrip() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val signatures = tx.sign(keyPair)
        val signedTx = tx.copy(signatures = signatures)

        val encoded = signedTx.encode()
        val decoded = SolanaTransaction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(signatures[0].bytes, decoded.signatures[0].bytes)
    }

    @Test
    fun encodeDecodeWithInstructionRoundtrip() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())
        val program = PublicKey(ByteArray(32) { 3 }.toList())

        val instruction = Instruction(
            program = program,
            accounts = listOf(AccountMeta.payer(payer)),
            data = listOf(0xCA.toByte(), 0xFE.toByte())
        )

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = listOf(instruction)
        )

        val encoded = tx.encode()
        val decoded = SolanaTransaction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(1, decoded.message.instructions.size)
        assertEquals(
            listOf(0xCA.toByte(), 0xFE.toByte()),
            decoded.message.instructions[0].data
        )
    }

    // --- Blockhash ---

    @Test
    fun recentBlockhashIsSettable() {
        val seed = ByteArray(32) { 1 }
        val keyPair = Ed25519.createKeyPair(seed)
        val payer = PublicKey(keyPair.publicKeyBytes.toList())

        val tx = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = testHash(),
            instructions = emptyList()
        )

        val newHash = Hash(ByteArray(32) { 0xCD.toByte() }.toList())
        tx.recentBlockhash = newHash
        assertEquals(newHash, tx.recentBlockhash)
    }
}
