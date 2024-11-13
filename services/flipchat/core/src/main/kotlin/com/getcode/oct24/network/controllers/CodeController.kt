package com.getcode.oct24.network.controllers

import com.getcode.model.KinAmount
import com.getcode.network.BalanceController
import com.getcode.network.repository.TransactionRepository
import com.getcode.services.annotations.EcdsaLookup
import com.getcode.services.model.EcdsaTuple
import javax.inject.Inject

class CodeController @Inject constructor(
    @EcdsaLookup
    private val storedEcda: () -> EcdsaTuple,
    private val balanceController: BalanceController,
    private val transactionRepository: TransactionRepository
) {
    suspend fun requestAirdrop(): Result<KinAmount> {
        val owner = storedEcda().algorithm ?: return Result.failure(Throwable("No owner"))
        return transactionRepository.requestFirstKinAirdrop(owner)
    }
}
//class CodeController @Inject constructor(
//    private val accountInfo: AccountInfoRepository,
//    private val currency: CurrencyRepository,
//    private val transactions: TransactionRepository
//) {
//    private suspend fun getAccountInfo(): Result<Map<PublicKey, AccountInfo>> {
//        return accountInfo.getAccountInfo()
//    }
//
//    private suspend fun getRateSnapshot(): Result<ExchangeRateSnapshot> {
//        return currency.getRates()
//    }
//
//    suspend fun fetchBalance() : Result<Kin> {
//        val (result, error) = getAccountInfo()
//        if (error != null) {
//            when (error) {
//                is AccountInfoService.GetAccountInfoError.NotFound -> {
//                    // Create account and requery
//                    println("Account needs creating")
//                }
//                else -> return Result.failure(error)
//            }
//        }
//
//        val accountInfo = result.orEmpty()
//
//
//    }
//
//    suspend fun requestAirdrop(): Result<KinAmount> {
//        return transactions.requestAirdrop()
//    }
//}
//
//private operator fun <T> Result<T>.component1(): T? {
//    return this.getOrNull()
//}
//
//private operator fun <T> Result<T>.component2(): AccountInfoService.GetAccountInfoError? {
//    return this.exceptionOrNull() as? AccountInfoService.GetAccountInfoError
//}
