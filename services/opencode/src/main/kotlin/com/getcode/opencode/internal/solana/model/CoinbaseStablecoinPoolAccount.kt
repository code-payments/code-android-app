package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.PublicKey

/**
 * Represents the on-chain CoinbaseStableSwapper liquidity pool account.
 *
 * Mirrors `LiquidityPool` in coinbase/stable-swapper (`programs/stable-swapper/src/state.rs`)
 * after the role-based authority migration (coinbase/stable-swapper#20). Layout:
 *
 * ```
 * [8 discriminator]
 * [32 pause_authority][32 unpause_authority][32 treasury_authority][32 configure_authority]
 * [32 fee_recipient]
 * [4 + 32n withdraw_recipients][4 + 32n supported_tokens]
 * [8 fee_rate][1 swaps_paused][1 liquidity_paused][1 bump]
 * ```
 *
 * Only the fixed-offset prefix is parsed; the client needs the fee recipient and nothing
 * after the vectors.
 */
internal data class CoinbaseStablecoinPoolAccount(
    val pauseAuthority: PublicKey,
    val unpauseAuthority: PublicKey,
    val treasuryAuthority: PublicKey,
    val configureAuthority: PublicKey,
    val feeRecipient: PublicKey,
) {
    companion object {
        // LiquidityPool discriminator: [66, 38, 17, 64, 188, 80, 68, 129]
        private val DISCRIMINATOR = byteArrayOf(66, 38, 17, 64, 188.toByte(), 80, 68, 129.toByte())

        private const val KEY_SIZE = 32
        private const val PAUSE_AUTHORITY_OFFSET = 8
        private const val UNPAUSE_AUTHORITY_OFFSET = PAUSE_AUTHORITY_OFFSET + KEY_SIZE
        private const val TREASURY_AUTHORITY_OFFSET = UNPAUSE_AUTHORITY_OFFSET + KEY_SIZE
        private const val CONFIGURE_AUTHORITY_OFFSET = TREASURY_AUTHORITY_OFFSET + KEY_SIZE
        private const val FEE_RECIPIENT_OFFSET = CONFIGURE_AUTHORITY_OFFSET + KEY_SIZE
        private const val FIXED_PREFIX_SIZE = FEE_RECIPIENT_OFFSET + KEY_SIZE

        fun fromAccountData(data: ByteArray): CoinbaseStablecoinPoolAccount {
            require(data.size >= FIXED_PREFIX_SIZE) {
                "Account data too short: expected at least $FIXED_PREFIX_SIZE bytes, got ${data.size}"
            }
            require(data.sliceArray(0 until DISCRIMINATOR.size).contentEquals(DISCRIMINATOR)) {
                "Account data is not a CoinbaseStableSwapper LiquidityPool (discriminator mismatch)"
            }
            return CoinbaseStablecoinPoolAccount(
                pauseAuthority = data.keyAt(PAUSE_AUTHORITY_OFFSET),
                unpauseAuthority = data.keyAt(UNPAUSE_AUTHORITY_OFFSET),
                treasuryAuthority = data.keyAt(TREASURY_AUTHORITY_OFFSET),
                configureAuthority = data.keyAt(CONFIGURE_AUTHORITY_OFFSET),
                feeRecipient = data.keyAt(FEE_RECIPIENT_OFFSET),
            )
        }

        private fun ByteArray.keyAt(offset: Int): PublicKey =
            PublicKey(sliceArray(offset until offset + KEY_SIZE).toList())
    }
}
