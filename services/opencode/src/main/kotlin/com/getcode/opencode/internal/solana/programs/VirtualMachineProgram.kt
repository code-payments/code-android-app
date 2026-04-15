package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class VirtualMachineProgram {

    enum class Command(val value: Byte) {
        unknown(0),
        initVm(1),
        transferForSwap(17),
        closeSwapAccountIfEmpty(19),
        ;
    }

    companion object {
        val address = PublicKey(
            Base58.decode("vmZ1WUq8SxjBWcaeTCvgJRZbS84R61uniFsQy5YMRTJ").toList()
        )
    }
}