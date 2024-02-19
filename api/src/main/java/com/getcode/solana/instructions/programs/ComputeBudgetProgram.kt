package com.getcode.solana.instructions.programs

import com.getcode.solana.Instruction
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import com.getcode.vendor.Base58

class ComputeBudgetProgram {
    companion object {
        val address = PublicKey(Base58.decode("ComputeBudget111111111111111111111111111111").toList())

        enum class Command {
            requestUnits,
            requestHeapFrame,
            setComputeUnitLimit,
            setComputeUnitPrice,
        }

        fun parse(instruction: Instruction): List<Byte> {
            return instruction.data.consume(8).remaining
        }
    }
}