package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.opencode.internal.solana.model.MessageAddressLookupTable
import com.getcode.opencode.internal.solana.utils.DataSlice.byteToUnsignedInt
import com.getcode.opencode.internal.solana.utils.DataSlice.consume
import com.getcode.opencode.internal.solana.utils.DataSlice.prefix
import com.getcode.opencode.internal.solana.utils.DataSlice.tail
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace

/**
 * Represents a Version 0 (V0) Solana transaction message.
 *
 * Introduced in the Solana 1.10.x era to support Versioned Transactions and Address Lookup Tables (ALTs).
 * Unlike the legacy message structure, V0 messages support dynamic address resolution, allowing transactions
 * to load more accounts than can fit in the standard instruction size limit.
 *
 * Structure includes:
 * - A version identifier (masked with the high bit set).
 * - A header specifying account read/write permissions.
 * - A list of static public keys (explicitly included in the transaction).
 * - A recent blockhash for transaction validity.
 * - Compiled instructions referencing indices in the static keys list or lookup tables.
 * - Address Lookup Table entries, which map to additional accounts not listed statically.
 *
 * @property header The message header containing signature counts and read-only counts.
 * @property staticAccountKeys The list of public keys explicitly included in the transaction message.
 * @property recentBlockhash The hash of a recent block, used for transaction expiration and deduplication.
 * @property instructions The list of compiled instructions to be executed.
 * @property addressLookupTables A list of pointers to on-chain Address Lookup Tables, allowing the transaction to reference many accounts efficiently.
 */
data class VersionedMessageV0(
    val header: MessageHeader,
    val staticAccountKeys: List<PublicKey>,
    var recentBlockhash: Hash,
    val instructions: List<CompiledInstruction>,
    val addressLookupTables: List<MessageAddressLookupTable>,
) {

    val description: String
        get() = "V0(header: $header, staticKeys: ${staticAccountKeys.count()}, lookups: ${addressLookupTables.count()})"

    fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add((MessageVersion.v0.ordinal + messageVersionSerializationOffset).toByte())
        data.addAll(header.encode().toList())
        data.addAll(ShortVec.encodeList(staticAccountKeys.map { it.bytes }))
        data.addAll(recentBlockhash.bytes)
        data.addAll(ShortVec.encodeList(instructions.map { it.encode() }))
        data.addAll(ShortVec.encodeList(addressLookupTables.map { it.encode() }))
        return data
    }

    companion object {
        fun newInstance(data: List<Byte>): VersionedMessageV0? {
            if (data.isEmpty()) {
                trace(type = TraceType.Error, message = "data is empty")
                return null
            }

            var payload = data

            // Message Version
            val (version, remainingPayload) = payload.consume(1)
            payload = remainingPayload

            if (version.first().byteToUnsignedInt() != (MessageVersion.v0.ordinal + messageVersionSerializationOffset)) {
                trace(type = TraceType.Error, message = "version is not v0")
                return null
            }
            // Decode Header (manually, without decompiling instructions)
            val (headerBytes, remainingPayload1) = remainingPayload.consume(MessageHeader.length)
            payload = remainingPayload1
            val header = MessageHeader.fromList(headerBytes)

            // Decode static account keys
            val (accountCount, accountData) = ShortVec.decodeLen(payload)
            trace(type = TraceType.Process, message = "static account count: $accountCount")

            val staticKeys = accountData.chunked(LENGTH_32).mapNotNull { chunk ->
                runCatching { PublicKey(chunk) }.getOrNull()
            }

            payload = accountData.tail(LENGTH_32 * accountCount)

            // Decode recent blockhash
            val (hashBytes, remainingPayload2) = payload.consume(LENGTH_32)
            payload = remainingPayload2
            val hash = runCatching { Hash(hashBytes) }.getOrNull()
            if (hash == null) {
                trace(type = TraceType.Error, message = "failed to decode blockhash")
                return null
            }

            // Decode compiled instructions (without decompiling yet)
            val (instructionCount, instructionsData) = ShortVec.decodeLen(payload)
            trace(type = TraceType.Process, message = "instruction count: $instructionCount")

            var remainingInstructionsData = instructionsData
            val compiledInstructions = mutableListOf<CompiledInstruction>()

            repeat(instructionCount) {
                val instruction = CompiledInstruction.fromList(remainingInstructionsData)
                if (instruction == null) {
                    trace(type = TraceType.Error, message = "failed to decode instruction")
                    return null
                }

                remainingInstructionsData = remainingInstructionsData.tail(instruction.byteLength)
                compiledInstructions.add(instruction)
            }

            payload = remainingInstructionsData

            // Decode Address Table Lookups
            val (altCount, lookupData) = ShortVec.decodeLen(payload)
            var remaining = lookupData

            val alts = mutableListOf<MessageAddressLookupTable>()
            repeat(altCount) {
                // public key
                if (remaining.count() < LENGTH_32) {
                    trace(type = TraceType.Error, message = "not enough data for lookup public key")
                    return null
                }

                val publicKeyData = remaining.prefix(LENGTH_32)
                remaining = remaining.drop(LENGTH_32)
                val publicKey = runCatching { PublicKey(publicKeyData) }.getOrNull()
                if (publicKey == null) {
                    trace(type = TraceType.Error, message = "failed to decode lookup public key")
                    return null
                }

                // writable indexes
                val (writableIndexLength, writableRemaining) = ShortVec.decodeLen(remaining)
                remaining = writableRemaining

                if (remaining.count() < writableIndexLength) {
                    trace(
                        type = TraceType.Error,
                        message = "not enough data for lookup writable indexes, need: $writableIndexLength, have: ${remaining.count()}"
                    )
                    return null
                }

                val writableIndexes = remaining.prefix(writableIndexLength)
                remaining = remaining.drop(writableIndexLength)

                // readonly indexes
                val (readonlyIndexLength, readonlyRemaining) = ShortVec.decodeLen(remaining)
                remaining = readonlyRemaining

                if (remaining.count() < readonlyIndexLength) {
                    trace(
                        type = TraceType.Error,
                        message = "not enough data for lookup readonly indexes, need: $readonlyIndexLength, have: ${remaining.count()}"
                    )
                    return null
                }

                val readonlyIndexes = remaining.prefix(readonlyIndexLength)
                remaining = remaining.drop(readonlyIndexLength)

                // Create the lookup entry
                val lookup = MessageAddressLookupTable(
                    publicKey = publicKey,
                    writableIndexes = writableIndexes,
                    readonlyIndexes = readonlyIndexes,
                )

                alts.add(lookup)
            }

            // now we have everything, return the v0 message
            return VersionedMessageV0(
                header = header,
                staticAccountKeys = staticKeys,
                recentBlockhash = hash,
                instructions = compiledInstructions,
                addressLookupTables = alts
            )
        }
    }
}