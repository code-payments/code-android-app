package com.getcode.solana.instructions.programs

import com.getcode.vendor.Base58

class ComputeBudgetProgram {

    enum class Command(val value: Byte) {
        requestUnits(0),
        requestHeapFrame(1),
        setComputeUnitLimit(2),
        setComputeUnitPrice(3),
    }

    companion object: com.getcode.solana.instructions.CommandType<Command>() {
        override val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("ComputeBudget111111111111111111111111111111").toList()
        )

        override val commandByteLength: Int
            get() = Byte.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.first() }
        }
    }
}