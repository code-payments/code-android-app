package com.getcode.solana.instructions.programs

import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.toLong
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.intToByteArray
import org.kin.sdk.base.tools.longToByteArray

class ComputeBudgetProgram_SetComputeUnitLimit(
    val limit: Long,
    val bump: Byte,
): InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = SystemProgram.address,
            accounts = emptyList(),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(ComputeBudgetProgram.Command.setComputeUnitLimit.ordinal.intToByteArray().toList())
        data.add(bump)
        data.addAll(limit.longToByteArray().toList())
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
            val limit = data.remaining.consume(stride).consumed.toByteArray().toLong()

            return ComputeBudgetProgram_SetComputeUnitLimit(bump = data.consumed.first(), limit = limit)
        }
    }
}