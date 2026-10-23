package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.utils.DataSlice.consume
import com.getcode.opencode.solana.Instruction
import com.getcode.utils.byteArrayToInt
import com.getcode.utils.intToByteArray

internal class ComputeBudgetProgram_SetComputeUnitLimit(
    val units: Int,
): InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = ComputeBudgetProgram.address,
            accounts = emptyList(),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(ComputeBudgetProgram.Command.setComputeUnitLimit.ordinal.toByte())
        data.addAll(units.intToByteArray().toList())
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): ComputeBudgetProgram_SetComputeUnitLimit {
            val data = ComputeBudgetProgram.parse(
                command = ComputeBudgetProgram.Command.setComputeUnitLimit,
                instruction = instruction,
                expectingAccounts = 0
            )

            val stride = UInt.SIZE_BYTES
            val units = data.remaining.consume(stride).consumed.toByteArray().byteArrayToInt()

            return ComputeBudgetProgram_SetComputeUnitLimit(units = units)
        }
    }
}