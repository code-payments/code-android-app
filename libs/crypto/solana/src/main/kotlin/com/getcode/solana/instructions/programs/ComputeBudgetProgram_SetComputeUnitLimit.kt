package com.getcode.solana.instructions.programs

import com.getcode.solana.Instruction
import com.getcode.utils.DataSlice.consume
import org.kin.sdk.base.tools.byteArrayToInt
import org.kin.sdk.base.tools.intToByteArray

class ComputeBudgetProgram_SetComputeUnitLimit(
    val limit: Int,
    val bump: Byte,
): com.getcode.solana.instructions.InstructionType {

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
//        data.add(bump)
        data.addAll(limit.intToByteArray().toList())
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
            val limit = data.remaining.consume(stride).consumed.toByteArray().byteArrayToInt()

            return ComputeBudgetProgram_SetComputeUnitLimit(bump = data.consumed.first(), limit = limit)
        }
    }
}