package com.getcode.opencode.repositories

import com.getcode.opencode.model.balance.Balance
import com.getcode.solana.keys.PublicKey

interface BalanceRepository {
    /**
     * Returns balance data for any owner account. Unauthenticated — no signing
     * key is required, only the account's address.
     */
    suspend fun getBalance(owner: PublicKey): Result<Balance>
}
