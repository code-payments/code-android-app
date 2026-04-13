package com.flipcash.app.tokens.core

import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.Flow

interface ReservesBalanceProvider {
    fun observeReservesBalance(): Flow<Fiat>
}
