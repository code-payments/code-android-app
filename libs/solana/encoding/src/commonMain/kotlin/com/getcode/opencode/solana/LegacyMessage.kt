package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.utils.DataSlice.chunk
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.tail
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.filterUniqueAccounts

/**
 * Represents a legacy Solana transaction message.
 *
 * A message is the core data part of a transaction that needs to be signed. It contains
 * the header, the list of all accounts involved in the transaction (signatures, read-only, etc.),
 * a recent blockhash to ensure liveness, and the instructions to be executed.
 *
 * This class handles the formatting, encoding, and decoding of the message payload
 * according to the legacy Solana transaction format (before versioned transactions).
 *
 * @property header The message header containing signature counts and read-only flags.
 * @property accounts A list of all unique accounts referenced by the instructions in this message.
 * @property recentBlockhash A recent blockhash used to ensure the transaction is processed quickly and not replayed.
 * @property instructions The list of instructions to be atomically executed by the runtime.
 */
data class LegacyMessage(
    val header: MessageHeader,
    val accounts: List<AccountMeta>,
    var recentBlockhash: Hash,
    val instructions: List<Instruction>,
) {
    fun encode(): ByteArray {
        val data = mutableListOf<Byte>()

        val accounts = accounts.map { it.publicKey }
        // Every caller that can construct a `LegacyMessage` builds this invariant in: within this
        // module, only `newInstance` below calls the constructor, and it derives `accounts` from
        // these same `instructions`. Across the Kotlin/Native boundary, `LegacyMessage` is not
        // itself exported — the exported type is `SharedSolanaLegacyMessage`
        // (`kmp/shared-core/spm/.../SolanaMessage.swift`), whose only public initializer validates
        // that every instruction's accounts are present in `accounts` and returns `nil` otherwise.
        // So a `LegacyMessage` with an instruction referencing an account missing from `accounts`
        // cannot exist, and `compile` returning `null` here is unreachable.
        val instructions = instructions.map {
            it.compile(accounts) ?: error("instruction references an account missing from this message")
        }

        data.addAll(header.encode().toList())
        data.addAll(ShortVec.encodeList(accounts.map { it.bytes }))
        data.addAll(recentBlockhash.bytes)
        data.addAll(ShortVec.encodeList(instructions.map { it.encode() }))

        return data.toByteArray()
    }

    companion object {
        fun newInstance(list: List<Byte>): LegacyMessage? {
            var payload: List<Byte> = list

            // Decode `header`. Guard the length explicitly: `MessageHeader.fromList` indexes
            // data[0..2] with no bounds check of its own (it's a pre-existing non-nullable API,
            // left as-is to avoid a public signature change), and `payload.consume` silently
            // hands back an empty `consumed` list rather than throwing when `payload` is shorter
            // than requested.
            if (payload.size < MessageHeader.length) return null
            val headerConsumed = payload.consume(MessageHeader.length)
            val header = MessageHeader.fromList(headerConsumed.consumed)
            payload = headerConsumed.remaining

            // Decode `accountKeys`
            val (accountCount, accountData) = ShortVec.decodeLen(payload) ?: return null
            if (accountCount < 0 || accountCount > accountData.size / com.getcode.solana.keys.LENGTH_32) return null
            val messageAccounts = accountData.chunk(com.getcode.solana.keys.LENGTH_32, accountCount) {
                com.getcode.solana.keys.PublicKey(
                    it
                )
            }
                ?: return null

            payload = accountData.tail(com.getcode.solana.keys.LENGTH_32 * accountCount)

            // Decode `recentBlockHash`. `Hash`/`Key32` never validate the size of the bytes handed
            // to them, so an under-length `payload` here would silently produce a corrupt hash
            // rather than fail — guard the length up front instead.
            if (payload.size < com.getcode.solana.keys.LENGTH_32) return null
            val hashConsumed = payload.consume(com.getcode.solana.keys.LENGTH_32)
            val hash = com.getcode.solana.keys.Hash(hashConsumed.consumed)

            payload = hashConsumed.remaining

            // Decode `instructions`
            var (instructionCount, remainingData) = ShortVec.decodeLen(payload) ?: return null
            val compiledInstructions = mutableListOf<CompiledInstruction>()

            for (i in 0 until instructionCount) {
                val instruction = CompiledInstruction.fromList(remainingData) ?: return null

                // `programIndex` is dropped here rather than range-checked: it's a signed `Byte`,
                // so a wire byte of 0xFF (out of range as an unsigned index) reads as -1 and
                // would pass a signed `>= messageAccounts.size` comparison anyway. The unsigned,
                // full-range check now lives in `CompiledInstruction.decompile` below, which
                // rejects both `programIndex` and every `accountIndexes` entry that's out of
                // range for `metaAccounts` — a signed check here would only contradict it.
                remainingData = remainingData.tail(instruction.byteLength)
                compiledInstructions.add(instruction)
            }

            val metaAccounts = messageAccounts.mapIndexed { index, account ->
                AccountMeta(
                    publicKey = account,
                    isSigner = index < header.requiredSignatures,
                    isWritable = index < header.requiredSignatures - header.readOnlySigners ||
                            index >= header.requiredSignatures && index < messageAccounts.size - header.readOnly,
                    isPayer = index == 0,
                    isProgram = false
                )
            }

            val instructions = compiledInstructions.mapNotNull { it.decompile(metaAccounts) }

            if (instructions.size != compiledInstructions.size) {
                return null
            }

            return LegacyMessage(header, metaAccounts, hash, instructions)
        }

        fun newInstance(
            accounts: List<AccountMeta>,
            recentBlockhash: com.getcode.solana.keys.Hash /* = com.getcode.solana.keys.Key32 */,
            instructions: List<Instruction>
        ): LegacyMessage {
            // Sort the account meta's based on:
            //   1. Payer is always the first account / signer.
            //   1. All signers are before non-signers.
            //   2. Writable accounts before read-only accounts.
            //   3. Programs last
            val uniqueAccounts = accounts.filterUniqueAccounts().sorted()

            val signers         = uniqueAccounts.filter { it.isSigner }
            val readOnlySigners = uniqueAccounts.filter { !it.isWritable && it.isSigner }
            val readOnly        = uniqueAccounts.filter { !it.isWritable && !it.isSigner }
            val header = MessageHeader(
                requiredSignatures = signers.size,
                readOnlySigners = readOnlySigners.size,
                readOnly = readOnly.size
            )

            return LegacyMessage(
                header,
                uniqueAccounts,
                recentBlockhash,
                instructions
            )
        }
    }
}
