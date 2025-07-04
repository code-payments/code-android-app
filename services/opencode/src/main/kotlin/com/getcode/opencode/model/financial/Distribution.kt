package com.getcode.opencode.model.financial

import com.getcode.solana.keys.PublicKey

/**
 * Represents a distribution of a specific fiat amount to a destination public key.
 *
 * @property destination The public key of the recipient of the distribution. This must ALWAYS be a primary account.
 * @property amount The amount of funds (in quarks) to distribute to the destination
 */
data class Distribution(
    val destination: PublicKey,
    val amount: Fiat,
)
