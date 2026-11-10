package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.solana.Instruction

@InternalOpenCodeApi public class MemoProgram_Memo(
    private val message: String,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = MemoProgram.address,
            accounts = emptyList(),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        return message.encodeToByteArray().toList()
    }
}