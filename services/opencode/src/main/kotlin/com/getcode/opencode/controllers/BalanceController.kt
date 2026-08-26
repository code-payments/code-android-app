package com.getcode.opencode.controllers

import com.getcode.opencode.model.balance.Balance
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceController @Inject constructor(
    private val balanceRepository: BalanceRepository,
) {
    /**
     * Returns balance data for any owner account.
     *
     * Unlike the rest of this package's controllers, this does not take an
     * `AccountCluster` — the underlying RPC is unauthenticated and unsigned, so a
     * bare [PublicKey] is all that's needed, and this can resolve balance for any
     * owner account, not just the current user's.
     */
    suspend fun getBalance(owner: PublicKey): Result<Balance> {
        return balanceRepository.getBalance(owner)
    }
}
