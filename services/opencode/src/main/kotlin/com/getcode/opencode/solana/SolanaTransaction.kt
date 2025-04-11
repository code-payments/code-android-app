package com.getcode.opencode.solana

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.opencode.internal.solana.programs.InstructionType
import com.getcode.opencode.internal.solana.utils.DataSlice.chunk
import com.getcode.opencode.internal.solana.utils.DataSlice.tail
import com.getcode.opencode.internal.solana.utils.printDiff
import com.getcode.opencode.internal.solana.utils.printMatch
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.keys.description

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

data class SolanaTransaction(val message: Message, val signatures: List<Signature>) {
    val identifier
        get() = signatures.first()

    var recentBlockhash
        get() = message.recentBlockhash
        set(v) {
            message.recentBlockhash = v
        }

    fun sign(vararg keyPairs: Ed25519.KeyPair): List<Signature> {
        val requiredSignatureCount = message.header.requiredSignatures
        if (keyPairs.size > requiredSignatureCount) {
            throw Exception(SigningError.tooManySigners.name)
        }

        val messageData = message.encode()
        val newSignatures = mutableListOf<Signature>()

        keyPairs.forEach { keyPair ->
            val signatureIndex =
                message.accounts.indexOfFirst { it.publicKey.bytes == keyPair.publicKeyBytes.toList() }
            if (signatureIndex == -1) {
                throw Exception("accountNotInAccountList. Account: ${keyPair.publicKey}")
            }

            val signature = Ed25519.sign(messageData, keyPair)
            newSignatures.add(Signature(signature.toList()))
        }

        return newSignatures
    }

    fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(ShortVec.encodeList(signatures.map { it.bytes }))
        data.addAll(message.encode().toList())
        return data
    }

    inline fun <reified T> findInstruction(cb: (instruction: Instruction) -> InstructionType): T? {
        message.instructions.forEach {
            try {
                val res = cb(it)
                if (res is T) {
                    return res
                }
            } catch (e: Exception) {
            }
        }
        return null
    }

    override fun toString(): String {
        return """
            SolanaTransaction {
                message=$message,
                signatures=${signatures.joinToString { it.base58() }}
            }
        """.trimIndent()
    }

    enum class SigningError {
        tooManySigners,
        accountNotInAccountList,
        invalidKey
    }

    companion object {
        fun fromList(list: List<Byte>): SolanaTransaction? {
            val (signatureCount, payload) = ShortVec.decodeLen(list)

            if (payload.size < signatureCount * com.getcode.solana.keys.LENGTH_64) {
                return null
            }

            val signatures: List<Signature> =
                payload.chunk(size = com.getcode.solana.keys.LENGTH_64, count = signatureCount) {
                    Signature(
                        it
                    )
                }.orEmpty()
            val messageData = payload.tail(signatureCount * com.getcode.solana.keys.LENGTH_64)
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

            val message = Message.newInstance(
                accounts = accounts,
                recentBlockhash = recentBlockhash ?: Hash.zero,
                instructions = instructions
            )

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
    }
}

internal fun SolanaTransaction.diff(other: SolanaTransaction) {
    val lhs = this
    val rhs = other

    if (lhs.identifier == rhs.identifier) {
        printMatch("ID")
    } else {
        printDiff(
            title = "ID",
            one = lhs.identifier.base58(),
            two = rhs.identifier.base58()
        )
    }

    if (lhs.signatures == rhs.signatures) {
        printMatch("Signatures")
    } else {
        printDiff(
            title = "Signatures",
            one = lhs.signatures.map { it.base58() },
            two = rhs.signatures.map { it.base58() }
        )
    }

    if (lhs.message.header == rhs.message.header) {
        printMatch("Header")
    } else {
        printDiff(
            title = "Header",
            one = lhs.message.header.description,
            two = rhs.message.header.description,
        )
    }

    if (lhs.message.recentBlockhash == rhs.message.recentBlockhash) {
        printMatch("Recent Blockhash")
    } else {
        printDiff(
            title = "Recent Blockhash",
            one = lhs.recentBlockhash.base58(),
            two = rhs.recentBlockhash.base58(),
        )
    }

    if (lhs.message.accounts == rhs.message.accounts) {
        printMatch("Accounts")
    } else {
        printDiff(
            title = "Accounts",
            one = lhs.message.accounts.map { it.description },
            two = rhs.message.accounts.map { it.description },
        )
    }

    if (lhs.message.instructions == rhs.message.instructions) {
        printMatch("Instructions")
    } else {
        printDiff(
            title = "Instructions",
            one = lhs.message.instructions.map { it.description },
            two = rhs.message.instructions.map { it.description },
        )
    }
}