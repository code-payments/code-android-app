package com.getcode.opencode.solana

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.prefix
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.solana.keys.description
import com.getcode.utils.hexEncodedString

data class Instruction(
    val program: PublicKey,
    val accounts: List<AccountMeta>,
    val data: List<Byte>,
) {
    /**
     * Compiles this instruction against [messageAccounts] by replacing [program] and each
     * account's [PublicKey] with its index into [messageAccounts].
     *
     * @return the compiled instruction, or `null` if [program] or any [accounts] entry is not
     *   present in [messageAccounts]. `indexOfFirst` returns `-1` on a miss, and `(-1).toByte()`
     *   is `0xFF` — a structurally valid but wrong index — so a miss is reported as `null` instead
     *   of silently compiling a corrupt instruction.
     */
    fun compile(messageAccounts: List<PublicKey>): CompiledInstruction? {
        val programIndex = messageAccounts.indexOfFirst { it == program }
        if (programIndex < 0) return null

        val accountIndexes = accounts.map { account ->
            val accountIndex = messageAccounts.indexOfFirst { it == account.publicKey }
            if (accountIndex < 0) return null
            accountIndex.toByte()
        }

        return CompiledInstruction(
            programIndex = programIndex.toByte(),
            accountIndexes = accountIndexes,
            data = data
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instruction) return false

        other as Instruction

        if (program != other.program) return false
        if (accounts != other.accounts) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        var result = program.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }

    companion object {
        // newInstruction creates a new instruction.
        fun newInstruction(
            program: PublicKey,
            data: List<Byte>,
            vararg accounts: AccountMeta,
        ): Instruction {
            return Instruction(
                program,
                accounts.asList(),
                data
            )
        }
    }
}

data class CompiledInstruction(
    val programIndex: Byte,
    val accountIndexes: List<Byte>,
    val data: List<Byte>,
) {
    val byteLength: Int by lazy {
        1 +
                ShortVec.encodeLen(accountIndexes.size).size +
                accountIndexes.size +
                ShortVec.encodeLen(data.size).size +
                data.size
    }

    val description: String
        get() {
            val accountIndexesStr = accountIndexes.joinToString(", ") { it.toString() }
            return "$programIndex $accountIndexesStr ${data.hexEncodedString()}"
        }


    fun encode(): List<Byte> {
        val container = mutableListOf<Byte>()
        container.add(programIndex)
        container.addAll(ShortVec.encode(accountIndexes).toList())
        container.addAll(ShortVec.encode(data).toList())
        return container
    }

    fun decompile(accounts: List<AccountMeta>): Instruction? {
        if (accounts.size < accountIndexes.size + 1) { // +1 for program
            return null
        }

        val program = accounts[programIndex.toInt()].publicKey
        val accountsD = accountIndexes.map { accounts[it.toInt()] }

        return Instruction(
            program = program,
            accounts = accountsD,
            data = data
        )
    }

    companion object {
        fun fromList(list: List<Byte>): CompiledInstruction? {
            if (list.size <= 1) {
                return null
            }

            var payload = list

            val indexConsumed = payload.consume(1)
            val index = indexConsumed.consumed.first()
            payload = indexConsumed.remaining

            val (accountCount, accountData) = ShortVec.decodeLen(payload) ?: return null
            if (accountData.size < accountCount) return null

            val accountIndexesConsumed = accountData.consume(accountCount)
            val accountIndexesRemaining = accountIndexesConsumed.remaining
            val accountIndexes = accountIndexesConsumed.consumed

            val (opaqueCount, opaqueData) = ShortVec.decodeLen(accountIndexesRemaining) ?: return null
            if (opaqueData.size < opaqueCount) return null

            return CompiledInstruction(
                index,
                accountIndexes,
                opaqueData.prefix(opaqueCount)
            )
        }
    }
}

val Instruction.description: String
    get() = """
        ${program.base58()} ${accounts.count()} ${data.hexEncodedString()}
        ${if (accounts.isNotEmpty()) accounts.joinToString { it.description } else ""}
    """.trimIndent()
