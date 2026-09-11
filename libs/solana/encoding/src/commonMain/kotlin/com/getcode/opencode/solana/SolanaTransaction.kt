package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.opencode.internal.solana.model.MessageAddressLookupTable
import com.getcode.utils.DataSlice.chunk
import com.getcode.utils.DataSlice.tail
import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.LENGTH_64
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.keys.filterUniqueAccounts

/*
    Signature: [64]byte
    PublicKey: [32]byte
    Hash:      [32]byte
    CompiledInstruction:
        program_id_index: byte            // index of the program account in message::AccountKeys
        accounts:         short_vec<byte> // ordered indices mapping to message::AccountKeys to input to program
        data:             short_vec<byte> // raw data
    Transaction:
        signature: short_vec<Signature>
        Message:
            Header:
                num_required_signatures:        byte
                num_readonly_signed_accounts:   byte
                num_readonly_unsigned_accounts: byte
            AccountKeys:     short_vec<PublicKey>
            RecentBlockHash: Hash
            Instructions:    short_vec<CompiledInstruction>
    Serialization:
        - Arrays: No length, just elements.
        - ShortVec: ShortVec encoded length, then elements
        - Byte: Byte
        - Structs: Fields are serialized in order as declared. No metadata about structs are serialized.
*/

/**
 * Represents a Solana transaction, which consists of a message and a list of signatures.
 *
 * A transaction acts as an atomic unit of execution on the Solana blockchain. It carries a [Message]
 * (containing instructions and account references) and a list of [Signature]s proving authorization
 * from the required account holders.
 *
 * @property message The transaction message containing headers, account keys, a recent blockhash, and instructions.
 * @property signatures A list of signatures corresponding to the signers required by the message.
 */
data class SolanaTransaction(val message: Message, val signatures: List<Signature>) {
    val identifier
        get() = signatures.first()

    var recentBlockhash
        get() = message.recentBlockhash
        set(v) {
            message.recentBlockhash = v
        }

    fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(ShortVec.encodeList(signatures.map { it.bytes }))
        data.addAll(message.encode().toList())
        return data
    }

    override fun toString(): String {
        return """
            SolanaTransaction {
                message=$message,
                signatures=${signatures.joinToString { it.base58() }}
            }
        """.trimIndent()
    }

    companion object {
        fun fromList(list: List<Byte>): SolanaTransaction? {
            val (signatureCount, payload) = ShortVec.decodeLen(list)

            if (payload.size < signatureCount * LENGTH_64) {
                return null
            }

            val signatures: List<Signature> =
                payload.chunk(size = LENGTH_64, count = signatureCount) { Signature(it) }.orEmpty()
            val messageData = payload.tail(signatureCount * LENGTH_64)
            val message = Message.newInstance(messageData) ?: return null

            return SolanaTransaction(signatures = signatures.toMutableList(), message = message)
        }

        fun newInstance(
            payer: PublicKey,
            recentBlockhash: Hash?,
            instructions: List<Instruction>
        ): SolanaTransaction {
            val accounts = mutableListOf<AccountMeta>()
            accounts.add(AccountMeta.payer(publicKey = payer))

            instructions.forEach {
                accounts.add(AccountMeta.program(publicKey = it.program))
                accounts.addAll(it.accounts)
            }

            val legacyMessage = LegacyMessage.newInstance(
                accounts = accounts,
                recentBlockhash = recentBlockhash ?: Hash.zero,
                instructions = instructions
            )

            val message = Message.Legacy(legacyMessage)

            val signatures = mutableListOf<Signature>()
                .apply {
                    for (i in 0 until message.header.requiredSignatures) {
                        add(Signature.zero)
                    }
                }

            return SolanaTransaction(
                message,
                signatures
            )
        }

        fun newV0Instance(
            payer: PublicKey,
            recentBlockhash: Hash?,
            addressLookupTables: List<AddressLookupTable>,
            instructions: List<Instruction>,
        ): SolanaTransaction {
            val hash = recentBlockhash ?: Hash.zero

            // Build initial account metas
            var accounts = mutableListOf<AccountMeta>()
            accounts.add(AccountMeta.payer(payer))

            // Add program accounts and instruction accounts
            instructions.forEach { instruction ->
                accounts.add(AccountMeta.program(instruction.program))
                accounts.addAll(instruction.accounts)
            }

            // Filter unique accounts (preserves highest permissions including isProgram)
            accounts = accounts.filterUniqueAccounts().toMutableList()

            // Sort accounts according to Solana's requirements (matches Go implementation):
            // 1. Payer first
            // 2. Programs last (non-program accounts come first)
            // 3. Signers before non-signers
            // 4. Writable before read-only
            // 5. Lexicographic ordering by public key (bytes.Compare)
            accounts.sortWith(Comparator { lhs, rhs ->
                // Payer is always first
                if (lhs.isPayer) return@Comparator -1
                if (rhs.isPayer) return@Comparator 1

                // Programs are always last (non-program accounts come first)
                if (lhs.isProgram != rhs.isProgram) {
                    return@Comparator if (lhs.isProgram) 1 else -1
                }

                // Signers before non-signers
                if (lhs.isSigner != rhs.isSigner) {
                    return@Comparator if (lhs.isSigner) -1 else 1
                }

                // Writable before read-only
                if (lhs.isWritable != rhs.isWritable) {
                    return@Comparator if (lhs.isWritable) -1 else 1
                }

                // Lexicographic ordering by public key (byte comparison)
                lhs.publicKey.compareTo(rhs.publicKey)
            })

            // Sort LUTs by public key
            val sortedLuts = addressLookupTables.sortedWith(Comparator { lhs, rhs ->
                lhs.publicKey.compareTo(rhs.publicKey)
            })

            // Collect LUT indexes by iterating through SORTED accounts
            val writableLUTIndexes = MutableList(sortedLuts.size) { mutableListOf<Byte>() }
            val readonlyLUTIndexes = MutableList(sortedLuts.size) { mutableListOf<Byte>() }

            val staticAccountKeys = mutableListOf<PublicKey>()

            var requiredSigners = 0
            var readOnlySigners = 0
            var readOnly = 0

            // Process each SORTED account to determine if it should be static or dynamically loaded
            accounts.forEach { account ->
                val pk = account.publicKey
                var isDynamicallyLoaded = false

                // Only non-signer, non-payer, non-program accounts can be dynamically loaded from LUTs
                if (!account.isSigner && !account.isPayer && !account.isProgram) {
                    var skip = false
                    sortedLuts.withIndex().forEach { (lutIndex, lut) ->
                        if (!skip) {
                            if (lut.addresses.contains(pk)) {
                                lut.addresses.indexOfFirst { pk == it }.takeIf { it >= 0 }
                                    ?.let { addressIndex ->
                                        isDynamicallyLoaded = true
                                        if (account.isWritable) {
                                            writableLUTIndexes[lutIndex].add(addressIndex.toByte())
                                        } else {
                                            readonlyLUTIndexes[lutIndex].add(addressIndex.toByte())
                                        }
                                        skip = true
                                    }
                            }
                        }
                    }
                }

                // If not dynamically loaded, add to static account keys
                if (!isDynamicallyLoaded) {
                    staticAccountKeys.add(pk)

                    if (account.isSigner) {
                        requiredSigners++

                        if (!account.isWritable) {
                            readOnlySigners++
                        }
                    } else if (!account.isWritable) {
                        readOnly++
                    }
                }
            }

            val header = MessageHeader(
                requiredSignatures = requiredSigners,
                readOnlySigners = readOnlySigners,
                readOnly = readOnly,
            )

            // Build complete account list for instruction compilation. Loaded
            // accounts resolve on-chain grouped BY TABLE — every table's writable
            // addresses first, then every table's readonly addresses — not in the
            // global sort order they were discovered in. With a single table the
            // two orderings coincide; with multiple tables only the table-grouped
            // order matches the runtime (and the server's expected transaction).
            val loadedWritableAccounts = sortedLuts.withIndex().flatMap { (lutIndex, lut) ->
                writableLUTIndexes[lutIndex].map { lut.addresses[it.toInt() and 0xFF] }
            }
            val loadedReadonlyAccounts = sortedLuts.withIndex().flatMap { (lutIndex, lut) ->
                readonlyLUTIndexes[lutIndex].map { lut.addresses[it.toInt() and 0xFF] }
            }
            val allAccounts = staticAccountKeys + loadedWritableAccounts + loadedReadonlyAccounts

            // Build address table lookups (only include LUTs that are actually used)
            val addressTableLookups = mutableListOf<MessageAddressLookupTable>()
            sortedLuts.withIndex().forEach { (lutIndex, lut) ->
                val writable = writableLUTIndexes[lutIndex]
                val readOnly = readonlyLUTIndexes[lutIndex]

                if (writable.isNotEmpty() || readOnly.isNotEmpty()) {
                    val lookup = MessageAddressLookupTable(
                        publicKey = lut.publicKey,
                        writableIndexes = writable,
                        readonlyIndexes = readOnly,
                    )

                    addressTableLookups.add(lookup)
                }
            }

            // Compile instructions using the complete account list
            val compiledInstructions = instructions.map { instruction ->
                instruction.compile(allAccounts)
            }

            // Create the V0 message
            val v0Message = VersionedMessageV0(
                header = header,
                staticAccountKeys = staticAccountKeys,
                recentBlockhash = hash,
                instructions = compiledInstructions,
                addressLookupTables = addressTableLookups
            )

            val message = Message.VersionedV0(v0Message)

            val signatures = mutableListOf<Signature>()
                    .apply {
                for (i in 0 until message.header.requiredSignatures) {
                    add(Signature.zero)
                }
            }

            return SolanaTransaction(
                message = message,
                signatures = signatures,
            )
        }
    }
}
