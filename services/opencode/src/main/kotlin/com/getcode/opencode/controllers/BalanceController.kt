package com.getcode.opencode.controllers

import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.repositories.BalanceRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class BalanceController @Inject constructor(
    private val repository: BalanceRepository
) {
    val balance: StateFlow<LocalFiat>
        get() = repository.balance

    fun onUserLoggedIn(cluster: AccountCluster) {
        repository.onUserLoggedIn(cluster)
    }

    suspend fun fetchBalance(): Result<Fiat> = repository.fetchBalance()

    fun reset() = repository.reset()
}