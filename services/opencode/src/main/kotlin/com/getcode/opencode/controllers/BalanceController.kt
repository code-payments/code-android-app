package com.getcode.opencode.controllers

import com.getcode.opencode.model.financial.OwnerBalance
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceController @Inject constructor(
    private val balanceRepository: BalanceRepository,
) {
    /**
     * Returns balance data for the given owner accounts, optionally filtered to a
     * set of mints.
     *
     * Unlike the rest of this package's controllers, this does not take an
     * `AccountCluster` — the underlying RPC is unauthenticated and unsigned, so
     * bare [PublicKey]s are all that's needed, and this can resolve balances for
     * any owner accounts, not just the current user's.
     */
    suspend fun getBalances(
        owners: List<PublicKey>,
        mints: List<PublicKey> = emptyList(),
    ): Result<List<OwnerBalance>> {
        return balanceRepository.getBalances(owners, mints)
    }
}
