package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.utils.DataSlice.toLong
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class SwapValidatorProgram {
    enum class Command(val value: Long) {
        preSwap("717F49CF8AC7DDB7".toULong(16).toLong()),
        postSwap("A1758AB339B7D59F".toULong(16).toLong())
    }

    companion object: CommandType<Command>() {
        override val address = PublicKey(
            Base58.decode("sWvA66HNNvgamibZe88v3NN5nQwE8tp3KitfViFjukA").toList()
        )
        override val commandByteLength: Int get() = Long.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.toLong() }
        }
    }
}

