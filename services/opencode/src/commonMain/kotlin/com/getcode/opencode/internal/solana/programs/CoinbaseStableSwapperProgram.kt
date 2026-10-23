package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey

internal class CoinbaseStableSwapperProgram {
    companion object {
        val address = PublicKey.fromBase58("pqgqKahpG1y2wsgxFhzaAnkV1cL9vk8MSg9qm4q646F")

        /**
         * The `swap_version` sent on server-mediated stablecoin swap requests, and the instruction
         * the client builds locally for the same transaction. Both must come from this one value:
         * the server builds with the version we request, so a mismatch fails the swap.
         * Exactly 2 selects SwapV2; anything else (including unset) is the legacy Swap.
         */
        const val SERVER_SWAP_VERSION: Int = 2
    }
}
