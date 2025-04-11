package com.getcode.opencode.repositories

import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import kotlinx.coroutines.flow.StateFlow

interface BalanceRepository {
    val balance: StateFlow<LocalFiat>
    fun onUserLoggedIn(cluster: AccountCluster)
    suspend fun fetchBalance(): Result<Fiat>
    fun reset()
}