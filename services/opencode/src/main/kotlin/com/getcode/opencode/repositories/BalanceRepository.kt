package com.getcode.opencode.repositories

import com.getcode.opencode.model.financial.OwnerBalance
import com.getcode.solana.keys.PublicKey

interface BalanceRepository {
    /**
     * Returns balance data for the given owner accounts, optionally filtered to a
     * set of mints. Each result entry carries the owner's core-mint (USDF) total
     * plus a per-mint breakdown; USDF is 6 decimals, which is the unit `Fiat`
     * already counts in.
     *
     * An owner with no balance for the requested mints is simply absent from the
     * result rather than represented as an error.
     *
     * Unauthenticated — no signing key is required, only the accounts' addresses.
     */
    suspend fun getBalances(
        owners: List<PublicKey>,
        mints: List<PublicKey> = emptyList(),
    ): Result<List<OwnerBalance>>
}
