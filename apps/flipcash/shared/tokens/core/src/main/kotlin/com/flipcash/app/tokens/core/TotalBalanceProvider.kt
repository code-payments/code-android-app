package com.flipcash.app.tokens.core

import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.Flow

/**
 * The account's holdings added up, for the surfaces that gate on a total rather than on any one
 * token — currently the username minimum-balance rule.
 *
 * A narrow interface here rather than a `:shared:tokens` dependency, for the same reason as
 * [ReservesBalanceProvider]: consumers get the number without the whole token stack (amount entry,
 * on-ramp, transaction history) coming with it.
 */
interface TotalBalanceProvider {
    fun observeTotalBalance(): Flow<Fiat>
}
