package com.getcode.solana.instructions.programs

import com.getcode.solana.Instruction
import com.getcode.solana.instructions.CommandType
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.byteArrayToInt
import org.kin.sdk.base.tools.byteArrayToLong

class ComputeBudgetProgram {

    enum class Command(val value: Byte) {
        requestUnits(0),
        requestHeapFrame(1),
        setComputeUnitLimit(2),
        setComputeUnitPrice(3),
    }

    companion object: CommandType<Command>() {
        override val address = PublicKey(Base58.decode("ComputeBudget111111111111111111111111111111").toList())

        override val commandByteLength: Int
            get() = Byte.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.first() }
        }
    }
}