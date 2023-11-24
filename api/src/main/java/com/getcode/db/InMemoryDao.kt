package com.getcode.db

import com.getcode.model.CurrencyRate
import com.getcode.model.SendLimit
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import io.reactivex.rxjava3.core.FlowableOnSubscribe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
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