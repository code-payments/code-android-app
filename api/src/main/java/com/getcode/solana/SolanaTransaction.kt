package com.getcode.solana

import com.getcode.ed25519.Ed25519
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.LENGTH_64
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.utils.DataSlice.chunk
import com.getcode.utils.DataSlice.tail


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
        set(v) { message.recentBlockhash = v }

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

    inline fun <reified T>findInstruction(cb: (instruction: Instruction) -> InstructionType): T? {
        message.instructions.forEach {
            try {
                val res = cb(it)
                if (res is T) {
                    return res
                }
            } catch (e: Exception) {}
        }
        return null
    }

    enum class SigningError {
        tooManySigners,
        accountNotInAccountList,
        invalidKey
    }

    companion object {
        fun fromList(list: List<Byte>): SolanaTransaction? {
            val (signatureCount, payload) = ShortVec.decodeLen(list)

            if (payload.size < signatureCount * LENGTH_64) {
                return null
            }

            val signatures: List<Signature> = payload.chunk(size = LENGTH_64, count = signatureCount) { Signature(it) } ?: listOf()
            val messageData = payload.tail(signatureCount * LENGTH_64)
            val message = Message.newInstance(messageData) ?: return null

            return SolanaTransaction(signatures = signatures.toMutableList(), message = message)
        }

        fun newInstance(
            payer: PublicKey,
            recentBlockhash: Hash?,
            instructions: List<Instruction>)
        : SolanaTransaction {
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
                    for(i in 0 until message.header.requiredSignatures) {
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