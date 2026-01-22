package com.getcode.opencode.internal.solana.programs

import com.getcode.vendor.Base58

internal class UsdfProgram {

    enum class Command(val value: Byte) {
        // initialize 1
        swap(2),
        transfer(3),
        ;
    }
    companion object {
        val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("usdfcP2V1bh1Lz7Y87pxR4zJd3wnVtssJ6GeSHFeZeu").toList()
        )
    }
}