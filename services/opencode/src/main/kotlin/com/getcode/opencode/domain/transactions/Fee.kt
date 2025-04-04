package com.getcode.opencode.domain.transactions

import com.getcode.solana.keys.PublicKey

/**
 * @param destination Destination Kin token account where the fee payment will be made
 * @param bps Fee percentage, in basis points, of the total quark amount of a payment.
 */
data class Fee(
    val destination: PublicKey,
    val bps: Int,
)