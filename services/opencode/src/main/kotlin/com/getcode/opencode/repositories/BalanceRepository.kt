package com.getcode.opencode.repositories

import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey

interface BalanceRepository {
    /**
     * Returns the owner's core-mint (USDF) balance. The response carries a raw quark count
     * and no currency code; USDF is 6 decimals, which is the unit [Fiat] already counts in.
     *
     * Unauthenticated — no signing key is required, only the account's address.
     */
    suspend fun getBalance(owner: PublicKey): Result<Fiat>
}
