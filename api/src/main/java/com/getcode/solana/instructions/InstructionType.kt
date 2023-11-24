package com.getcode.solana.instructions

import com.getcode.solana.Instruction

interface InstructionType {
    fun instruction(): Instruction
    fun encode(): List<Byte>
}