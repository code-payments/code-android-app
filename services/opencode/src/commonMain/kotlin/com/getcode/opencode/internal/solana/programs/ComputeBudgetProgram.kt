package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class ComputeBudgetProgram {

    enum class Command(val value: Byte) {
        requestUnits(0),
        requestHeapFrame(1),
        setComputeUnitLimit(2),
        setComputeUnitPrice(3),
    }

    companion object: CommandType<Command>() {
        override val address = PublicKey(
            Base58.decode("ComputeBudget111111111111111111111111111111").toList()
        )

        override val commandByteLength: Int
            get() = Byte.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.first() }
        }
    }
}