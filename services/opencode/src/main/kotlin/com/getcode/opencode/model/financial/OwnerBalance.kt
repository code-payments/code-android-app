package com.getcode.opencode.model.financial

import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * Balance data for a single owner account, as returned by `Balance.GetBalances`.
 *
 * [coreMintValue] is the owner's total across all mints, denominated in the core
 * mint. [balancesByMint] breaks that total down per mint; a mint the owner holds
 * no balance in is simply absent from the map.
 */
data class OwnerBalance(
    val owner: PublicKey,
    val coreMintValue: Fiat,
    val balancesByMint: Map<Mint, Fiat> = emptyMap(),
)
