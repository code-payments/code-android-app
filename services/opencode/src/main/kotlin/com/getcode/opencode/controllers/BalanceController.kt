package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.utils.trace
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class BalanceController @Inject constructor(
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
) {
    val balance: StateFlow<LocalFiat>
        get() = balanceRepository.balance

    fun onUserLoggedIn(cluster: AccountCluster) {
        balanceRepository.onUserLoggedIn(cluster)
    }

    suspend fun fetchBalance(): Result<Fiat> = balanceRepository.fetchBalance()

    suspend fun airdrop(
        destination: KeyPair,
        type: AirdropType
    ): Result<Unit> {
        return transactionRepository.airdrop(
            type = type,
            destination = destination
        ).onSuccess {
            trace("Airdrop was successful.")
            balanceRepository.fetchBalance()
        }.map { Unit }
    }

    fun reset() = balanceRepository.reset()
}