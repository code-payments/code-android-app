package com.getcode.solana.instructions.programs

import com.getcode.utils.DataSlice.toLong
import com.getcode.vendor.Base58

class SwapValidatorProgram {
    enum class Command(val value: Long) {
        preSwap("717F49CF8AC7DDB7".toULong(16).toLong()),
        postSwap("A1758AB339B7D59F".toULong(16).toLong())
    }

    companion object: com.getcode.solana.instructions.CommandType<Command>() {
        override val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("sWvA66HNNvgamibZe88v3NN5nQwE8tp3KitfViFjukA").toList()
        )
        override val commandByteLength: Int get() = Long.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.toLong() }
        }
    }
}

