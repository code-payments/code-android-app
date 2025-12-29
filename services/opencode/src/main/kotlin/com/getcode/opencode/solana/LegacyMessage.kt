package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.opencode.internal.solana.utils.DataSlice.chunk
import com.getcode.opencode.internal.solana.utils.DataSlice.consume
import com.getcode.opencode.internal.solana.utils.DataSlice.tail
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
        val instructions = instructions.map { it.compile(accounts) }

        data.addAll(header.encode().toList())
        data.addAll(ShortVec.encodeList(accounts.map { it.bytes }))
        data.addAll(recentBlockhash.bytes)
        data.addAll(ShortVec.encodeList(instructions.map { it.encode() }))

        return data.toByteArray()
    }

    companion object {
        fun newInstance(list: List<Byte>): LegacyMessage? {
            var payload: List<Byte> = list

            // Decode `header`
            val headerConsumed = payload.consume(MessageHeader.length)
            val header = MessageHeader.fromList(headerConsumed.consumed)
            payload = headerConsumed.remaining

            // Decode `accountKeys`
            val (accountCount, accountData) = ShortVec.decodeLen(payload)
            val messageAccounts = accountData.chunk(com.getcode.solana.keys.LENGTH_32, accountCount) {
                com.getcode.solana.keys.PublicKey(
                    it
                )
            }
                ?: return null

            payload = accountData.tail(com.getcode.solana.keys.LENGTH_32 * accountCount)

            // Decode `recentBlockHash`
            val hashConsumed = payload.consume(com.getcode.solana.keys.LENGTH_32)
            val hash = com.getcode.solana.keys.Hash(hashConsumed.consumed)

            payload = hashConsumed.remaining

            // Decode `instructions`
            var (instructionCount, remainingData) = ShortVec.decodeLen(payload)
            val compiledInstructions = mutableListOf<CompiledInstruction>()

            for (i in 0 until instructionCount) {
                val instruction = CompiledInstruction.fromList(remainingData) ?: return null

                if (instruction.programIndex >= messageAccounts.size) {
                    return null
                }

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
