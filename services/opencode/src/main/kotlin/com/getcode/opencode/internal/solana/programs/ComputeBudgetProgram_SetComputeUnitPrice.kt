package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.utils.DataSlice.consume
import com.getcode.opencode.solana.Instruction
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray

internal class ComputeBudgetProgram_SetComputeUnitPrice(
    val microLamports: Long,
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
        data.add(ComputeBudgetProgram.Command.setComputeUnitPrice.ordinal.toByte())
        data.addAll(microLamports.longToByteArray().toList())
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): ComputeBudgetProgram_SetComputeUnitPrice {
            val data = ComputeBudgetProgram.parse(
                command = ComputeBudgetProgram.Command.setComputeUnitPrice,
                instruction = instruction,
                expectingAccounts = 0
            )
            val microLamports = data.remaining.consume(8).consumed.toByteArray().byteArrayToLong()

            return ComputeBudgetProgram_SetComputeUnitPrice(microLamports = microLamports)
        }
    }
}