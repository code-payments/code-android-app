package com.getcode.network.repository


import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceRepository @Inject constructor() {

    val balanceFlow = MutableStateFlow(-1.0)

    fun setBalance(balance: Double) {
        balanceFlow.value = balance
    }

    fun clearBalance() {
        balanceFlow.value = 0.0
    }

}
