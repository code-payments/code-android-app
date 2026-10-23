package com.getcode.opencode.internal.solana.model

import com.getcode.solana.keys.PublicKey

/**
 * Represents the on-chain CoinbaseStableSwapper liquidity pool account.
 *
 * Layout (as deployed on mainnet, decoded from the live pool account
 * `CrDL9SoCyW1tBgn8k7rgGSpWhnszneWDbvKvqPAU4PL9`, owner program
 * `pqgqKahpG1y2wsgxFhzaAnkV1cL9vk8MSg9qm4q646F`):
 *
 * ```
 *   0   [8]  discriminator          sha256("account:LiquidityPool")[0..8]
 *   8   [32] operations_authority
 *   40  [32] pause_authority
 *   72  [32] unnamed pubkey         not described by the published IDL
 *   104 [32] unnamed pubkey         not described by the published IDL
 *   136 [32] fee_recipient
 *   168      vec<pubkey>            (not read by this model)
 *   ...
 * ```
 *
 * The published Anchor IDL (`scaas_liquidity` 0.1.0) does not describe this struct. It lists
 * three pubkeys (`operations_authority`, `pause_authority`, `fee_recipient`) followed directly
 * by `supported_tokens`, which puts `fee_recipient` at offset 72; mainnet has it at 136.
 * Regenerating this offset from the IDL reproduces the bug, and neither of the two pubkeys the
 * IDL omits has a name in any published source. Sending offset 72 as `fee_recipient` fails
 * on-chain with Anchor `ConstraintAddress` (error 2012 / `0x7dc`), which is what shipped in
 * production.
 */
internal data class CoinbaseStablecoinPoolAccount(
    val feeRecipient: PublicKey,
) {
    companion object {
        private val DISCRIMINATOR = byteArrayOf(66, 38, 17, 64, -68, 80, 68, -127)

        // discriminator(8) + operations_authority(32) + pause_authority(32) + two unnamed
        // pubkeys (32 each) that the published IDL does not describe
        private const val FEE_RECIPIENT_OFFSET = 8 + 32 + 32 + 32 + 32

        fun fromAccountData(data: ByteArray): CoinbaseStablecoinPoolAccount {
            require(data.size >= FEE_RECIPIENT_OFFSET + 32) {
                "Account data too short: expected at least ${FEE_RECIPIENT_OFFSET + 32} bytes, got ${data.size}"
            }
            val discriminator = data.sliceArray(0 until DISCRIMINATOR.size)
            require(discriminator.contentEquals(DISCRIMINATOR)) {
                "Unexpected account discriminator: expected ${DISCRIMINATOR.toList()}, got ${discriminator.toList()}"
            }
            val feeRecipientBytes = data.sliceArray(FEE_RECIPIENT_OFFSET until FEE_RECIPIENT_OFFSET + 32)
            return CoinbaseStablecoinPoolAccount(
                feeRecipient = PublicKey(feeRecipientBytes.toList()),
            )
        }
    }
}
