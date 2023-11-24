package com.getcode.solana

import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.prefix

data class Instruction constructor(
    val program: PublicKey,
    val accounts: List<AccountMeta>,
    val data: List<Byte>,
) {
    fun compile(messageAccounts: List<PublicKey>): CompiledInstruction {
        val programIndex = messageAccounts.indexOfFirst { it == program }
        val accountIndexes = accounts.map { account ->
            messageAccounts.indexOfFirst { it == account.publicKey }.toByte()
        }

        return CompiledInstruction(
            programIndex = programIndex.toByte(),
            accountIndexes = accountIndexes,
            data = data
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

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
        // NewInstruction creates a new instruction.
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

            var (accountCount, accountData) = ShortVec.decodeLen(payload)
            if (accountData.size < accountCount) return null

            val accountIndexesConsumed = accountData.consume(accountCount)
            accountData = accountIndexesConsumed.remaining
            val accountIndexes = accountIndexesConsumed.consumed

            val (opaqueCount, opaqueData) = ShortVec.decodeLen(accountData)
            if(opaqueData.size < opaqueCount) return null

            return CompiledInstruction(
                index,
                accountIndexes,
                opaqueData.prefix(opaqueCount)
            )
        }
    }
}
