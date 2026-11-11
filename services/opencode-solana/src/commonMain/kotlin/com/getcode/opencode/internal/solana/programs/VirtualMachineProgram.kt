package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

@InternalOpenCodeApi public class VirtualMachineProgram {

    enum class Command(val value: Byte) {
        unknown(0),
        initVm(1),
        transferForSwap(17),
        closeSwapAccountIfEmpty(19),
        transferForSwapWithFee(20),
        ;
    }

    companion object {
        val address = PublicKey(
            Base58.decode("vmZ1WUq8SxjBWcaeTCvgJRZbS84R61uniFsQy5YMRTJ").toList()
        )
    }
}