package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class VirtualMachineProgram {
    companion object {
        val address = PublicKey(
            Base58.decode("vmZ1WUq8SxjBWcaeTCvgJRZbS84R61uniFsQy5YMRTJ").toList()
        )
    }
}