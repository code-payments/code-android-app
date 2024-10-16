package com.getcode.solana.instructions

import com.getcode.solana.Instruction
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice
import com.getcode.utils.DataSlice.consume

interface ProgramType {
    val address: PublicKey
}

abstract class CommandType<Definition: Enum<Definition>>: ProgramType {
    abstract val commandByteLength: Int
    abstract fun commandLookup(bytes: ByteArray): Definition?
    fun ensure(instruction: Instruction, command: Definition) {
        val stride = commandByteLength
        if (instruction.data.size < stride) {
            throw CommandParseError.CommandNotFound()
        }

        val bytes = ByteArray(stride) { 0 }
        instruction.data.take(stride).toByteArray().copyInto(bytes)

        val parsedCommand = commandLookup(bytes)
        if (parsedCommand != command) {
            throw CommandParseError.PayloadNotFound()
        }
    }

    fun parse(command: Definition, instruction: Instruction, expectingAccounts: Int?): DataSlice.ByteListConsume {
        if (instruction.program != address) throw CommandParseError.InstructionMismatch()
        val ensured = ensure(instruction, command)

        if (expectingAccounts != null) {
            if (instruction.accounts.count() != expectingAccounts) {
                throw CommandParseError.AccountMismatch()
            }
        }

        return instruction.data.consume(commandByteLength)
    }
}



interface InstructionType {
    fun instruction(): Instruction
    fun encode(): List<Byte>
}

inline fun <reified T : Enum<T>> commandByteLength(): Int {
    return java.lang.Long.SIZE / java.lang.Byte.SIZE
}

sealed class CommandParseError : Exception() {
    class InstructionMismatch: CommandParseError()
    class CommandNotFound: CommandParseError()
    class PayloadNotFound: CommandParseError()
    class AccountMismatch: CommandParseError()
}