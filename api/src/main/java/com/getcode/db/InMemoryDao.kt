package com.getcode.db

import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryDao @Inject constructor(
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository
) {

    var inviteCount: Int? = null

    fun clear() {
        inviteCount = null
        balanceRepository.clearBalance()
        transactionRepository.clear()
    }
}