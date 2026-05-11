package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.PublicKey

/**
 * Represents the on-chain CoinbaseStableSwapper liquidity pool account.
 *
 * Layout:
 * [8 discriminator][32 operations_authority][32 pause_authority][32 fee_recipient]...
 */
internal data class CoinbaseStablecoinPoolAccount(
    val feeRecipient: PublicKey,
) {
    companion object {
        private const val FEE_RECIPIENT_OFFSET = 8 + 32 + 32 // discriminator + ops_authority + pause_authority

        fun fromAccountData(data: ByteArray): CoinbaseStablecoinPoolAccount {
            require(data.size >= FEE_RECIPIENT_OFFSET + 32) {
                "Account data too short: expected at least ${FEE_RECIPIENT_OFFSET + 32} bytes, got ${data.size}"
            }
            val feeRecipientBytes = data.sliceArray(FEE_RECIPIENT_OFFSET until FEE_RECIPIENT_OFFSET + 32)
            return CoinbaseStablecoinPoolAccount(
                feeRecipient = PublicKey(feeRecipientBytes.toList()),
            )
        }
    }
}
