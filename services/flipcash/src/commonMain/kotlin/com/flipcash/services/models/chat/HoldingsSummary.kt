package com.flipcash.services.models.chat

import com.getcode.solana.keys.Mint
import kotlin.time.Instant

/**
 * What a group's members held of the currency the group is gated on, as of [asOf].
 *
 * @param mint The currency the group is gated on
 * @param quarks Total held by members, in the mint's quarks (not a fiat amount)
 * @param holderCount Number of members who held any of the currency
 * @param asOf When the snapshot was computed
 */
data class HoldingsSummary(
    val mint: Mint,
    val quarks: Long,
    val holderCount: Long,
    val asOf: Instant,
)
