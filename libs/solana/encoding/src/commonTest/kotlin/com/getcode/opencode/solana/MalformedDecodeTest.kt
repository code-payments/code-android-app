package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.opencode.internal.solana.model.MessageAddressLookupTable
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.LENGTH_64
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression tests for the crash fixed in `ShortVec.decodeLen`
 * (`libs/solana/encoding/.../internal/solana/ShortVec.kt`): an unbounded `input[offset]` read
 * that threw `IndexOutOfBoundsException` on empty input, or on a ShortVec length prefix whose
 * last byte still had its continuation bit set.
 *
 * These exercise every decode entry point that reads a `decodeLen`-prefixed field from
 * untrusted bytes — a transaction, a legacy message, a v0 message, and a compiled instruction —
 * with malformed input at each stage: empty, a lone unterminated ShortVec byte, and truncation
 * part-way through a structurally valid-looking payload. Every case must return `null`, never
 * throw.
 */
class MalformedDecodeTest {

    private fun publicKey(seed: Int): PublicKey {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return PublicKey(bytes.toList())
    }

    private fun hash(seed: Int): Hash {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return Hash(bytes.toList())
    }

    // --- SolanaTransaction.fromList ---

    @Test
    fun solanaTransactionFromListEmptyReturnsNull() {
        assertNull(SolanaTransaction.fromList(emptyList()))
    }

    @Test
    fun solanaTransactionFromListLoneContinuationByteReturnsNull() {
        // A single 0xFF as the signature-count ShortVec: continuation bit set, no next byte.
        assertNull(SolanaTransaction.fromList(listOf(0xFF.toByte())))
    }

    @Test
    fun solanaTransactionFromListTruncatedAtEachStageReturnsNull() {
        val payer = publicKey(1)
        val instruction = Instruction(
            program = publicKey(3),
            accounts = listOf(AccountMeta.writable(publicKey(2), signer = true)),
            data = listOf(0x01, 0x02, 0x03),
        )
        val transaction = SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = hash(1),
            instructions = listOf(instruction),
        )
        val encoded = transaction.encode()

        // Sanity check: the well-formed encoding decodes successfully first, so every failure
        // below is attributable to the truncation and not to a construction mistake.
        assertNotNull(SolanaTransaction.fromList(encoded))

        val legacy = (transaction.message as Message.Legacy).message
        val sigCount = transaction.signatures.size
        val sigSectionLen = ShortVec.encodeLen(sigCount).size + sigCount * LENGTH_64
        val accountCount = legacy.accounts.size
        val accountsSectionLen = ShortVec.encodeLen(accountCount).size + accountCount * LENGTH_32
        val headerEnd = sigSectionLen + MessageHeader.length
        val accountsEnd = headerEnd + accountsSectionLen
        val hashEnd = accountsEnd + LENGTH_32

        // Truncated mid-signature: one byte short of the full signature block.
        assertNull(SolanaTransaction.fromList(encoded.take(sigSectionLen - 1)))
        // Truncated mid-header: only 1 of MessageHeader's 3 bytes present.
        assertNull(SolanaTransaction.fromList(encoded.take(sigSectionLen + 1)))
        // Truncated mid-account-keys: one byte short of a full PublicKey list.
        assertNull(SolanaTransaction.fromList(encoded.take(accountsEnd - 1)))
        // Truncated mid-recent-blockhash: one byte short of the 32-byte hash.
        assertNull(SolanaTransaction.fromList(encoded.take(hashEnd - 1)))
        // Truncated mid-instruction: the last instruction's final data byte missing.
        assertNull(SolanaTransaction.fromList(encoded.dropLast(1)))
    }

    // --- LegacyMessage.newInstance ---

    @Test
    fun legacyMessageNewInstanceEmptyReturnsNull() {
        assertNull(LegacyMessage.newInstance(emptyList()))
    }

    @Test
    fun legacyMessageNewInstanceLoneContinuationByteReturnsNull() {
        assertNull(LegacyMessage.newInstance(listOf(0xFF.toByte())))
    }

    @Test
    fun legacyMessageNewInstanceTruncatedReturnsNull() {
        val accounts = listOf(
            AccountMeta.payer(publicKey(1)),
            AccountMeta.writable(publicKey(2)),
        )
        val instruction = Instruction(
            program = publicKey(3),
            accounts = listOf(AccountMeta.writable(publicKey(2))),
            data = listOf(0x0A, 0x0B),
        )
        val message = LegacyMessage.newInstance(
            accounts = accounts + AccountMeta.program(publicKey(3)),
            recentBlockhash = hash(9),
            instructions = listOf(instruction),
        )
        val encoded = message.encode().toList()

        assertNotNull(LegacyMessage.newInstance(encoded))

        // Truncated before the header is fully present.
        assertNull(LegacyMessage.newInstance(encoded.take(1)))
        assertNull(LegacyMessage.newInstance(encoded.take(MessageHeader.length - 1)))
        // Truncated mid-instruction.
        assertNull(LegacyMessage.newInstance(encoded.dropLast(1)))
    }

    // --- VersionedMessageV0.newInstance ---

    @Test
    fun versionedMessageV0NewInstanceEmptyReturnsNull() {
        assertNull(VersionedMessageV0.newInstance(emptyList()))
    }

    @Test
    fun versionedMessageV0NewInstanceLoneContinuationByteReturnsNull() {
        // Valid version-prefix byte (0x80), followed by nothing — decodeLen for the account-key
        // count is never even reached; the header-length guard fires first.
        assertNull(VersionedMessageV0.newInstance(listOf(0x80.toByte())))
    }

    @Test
    fun versionedMessageV0NewInstanceTruncatedReturnsNull() {
        val v0 = VersionedMessageV0(
            header = MessageHeader(requiredSignatures = 1, readOnlySigners = 0, readOnly = 1),
            staticAccountKeys = listOf(publicKey(1), publicKey(2)),
            recentBlockhash = hash(1),
            instructions = listOf(CompiledInstruction(0, listOf(1), listOf(0x0A, 0x0B))),
            addressLookupTables = listOf(
                MessageAddressLookupTable(publicKey(50), listOf(0), listOf(1))
            ),
        )
        val encoded = v0.encode()

        assertNotNull(VersionedMessageV0.newInstance(encoded))

        // Truncated right after the version byte: header entirely missing.
        assertNull(VersionedMessageV0.newInstance(encoded.take(1)))
        // Truncated mid-header: only 1 of 3 header bytes present after the version byte.
        assertNull(VersionedMessageV0.newInstance(encoded.take(1 + 1)))
        // Truncated mid-static-account-keys. This is also the regression case for a bug found
        // alongside the crash fix: this path used to decode static keys with stdlib `chunked()` +
        // `runCatching { PublicKey(chunk) }.getOrNull()`, which never throws (PublicKey accepts
        // any-length input), so a truncated key list was silently accepted instead of failing.
        // It now uses the module's bounds-checked `DataSlice.chunk`, so this must return null.
        val versionAndHeaderLen = 1 + MessageHeader.length
        val staticKeysSectionLen = ShortVec.encodeLen(2).size + 2 * LENGTH_32
        assertNull(
            VersionedMessageV0.newInstance(
                encoded.take(versionAndHeaderLen + staticKeysSectionLen - 1)
            )
        )
        // Truncated mid-address-lookup-table (the final field): last readonly-index byte missing.
        assertNull(VersionedMessageV0.newInstance(encoded.dropLast(1)))
    }

    // --- CompiledInstruction.fromList ---

    @Test
    fun compiledInstructionFromListTruncatedAccountIndexesReturnsNull() {
        // programIndex=0, ShortVec accountCount=2, but only one index byte follows.
        val bytes = listOf(0.toByte(), 2.toByte(), 5.toByte())
        assertNull(CompiledInstruction.fromList(bytes))
    }

    @Test
    fun compiledInstructionFromListTruncatedDataReturnsNull() {
        // programIndex=0, accountCount=1 with index byte 0, opaque data length=5 but only 2
        // data bytes actually follow.
        val bytes = listOf(0.toByte(), 1.toByte(), 0.toByte(), 5.toByte(), 0xAA.toByte(), 0xBB.toByte())
        assertNull(CompiledInstruction.fromList(bytes))
    }
}
