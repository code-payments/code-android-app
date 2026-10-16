package com.flipcash.services.models.chat

import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.time.Instant

/**
 * What a group's members held of the currency the group is gated on, as of [asOf].
 *
 * @param mint The currency the group is gated on
 * @param holdings Total held by members, in the mint's quarks
 * @param holderCount Number of members who held any of the currency
 * @param asOf When the snapshot was computed
 */
data class HoldingsSummary(
    val mint: Mint,
    val holdings: Fiat,
    val holderCount: Long,
    val asOf: Instant,
)
