package com.getcode.network

import android.content.Context
import com.getcode.manager.SessionManager
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BalanceController @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val privacyMigration: PrivacyMigration,
    private val transactionReceiver: TransactionReceiver
) {

    fun observe(): Flow<Double> = balanceRepository.balanceFlow

    fun setTray(organizer: Organizer, tray: Tray) {
        organizer.set(tray)
        balanceRepository.setBalance(organizer.availableBalance.toKinTruncatingLong().toDouble())
    }

    fun fetchBalance(): Completable {
        if (SessionManager.isAuthenticated() != true) {
            Timber.d("FetchBalance - Not authenticated")
            return Completable.complete()
        }
        val owner = SessionManager.getKeyPair() ?: return Completable.error(IllegalStateException("Missing Owner"))

        fun getTokenAccountInfos(): Completable {
            return accountRepository.getTokenAccountInfos(owner)
                .flatMapCompletable { infos ->
                    val organizer = SessionManager.getOrganizer() ?:
                    return@flatMapCompletable Completable.error(IllegalStateException("Missing Organizer"))

                    organizer.setAccountInfo(infos)
                    balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
                    transactionReceiver.receiveFromIncomingIfRotationRequired(organizer)
                }
                .timeout(15, TimeUnit.SECONDS)
        }

        return getTokenAccountInfos()
            .doOnSubscribe {
                Timber.i("Fetching Balance account info")
            }
            .onErrorResumeNext {
                Timber.i("Error: ${it.javaClass.simpleName} ${it.cause}")
                val organizer = SessionManager.getOrganizer() ?: return@onErrorResumeNext Completable.error(IllegalStateException("Missing Organizer"))

                when (it) {
                    is AccountRepository.FetchAccountInfosException.MigrationRequiredException -> {
                        val amountToMigrate = it.accountInfo.balance
                        privacyMigration.migrateToPrivacy(
                            context = context,
                            amountToMigrate = amountToMigrate,
                            organizer = organizer
                        )
                            .ignoreElement()
                            .concatWith(getTokenAccountInfos())
                    }
                    is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                        transactionRepository.createAccounts(
                            organizer = organizer
                        )
                            .ignoreElement()
                            .concatWith(getTokenAccountInfos())
                    }
                    else -> {
                        Completable.error(it)
                    }
                }
            }
    }



    suspend fun fetchBalanceSuspend() {
        Timber.d("fetchBalance")
        if (SessionManager.isAuthenticated() != true) {
            Timber.d("FetchBalance - Not authenticated")
            return
        }
        val owner = SessionManager.getKeyPair() ?: throw IllegalStateException("Missing Owner")

        try {
            withTimeout(15000) {
                val accountInfo = accountRepository.getTokenAccountInfos(owner).blockingGet()
                val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")

                Timber.d("updating balance and organizer")
                organizer.setAccountInfo(accountInfo)
                balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
                transactionReceiver.receiveFromIncomingIfRotationRequired(organizer)
            }
        } catch (ex: Exception) {
            Timber.i("Error: ${ex.javaClass.simpleName} ${ex.cause}")
            val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")

            when (ex) {
                is AccountRepository.FetchAccountInfosException.MigrationRequiredException -> {
                    val amountToMigrate = ex.accountInfo.balance
                    privacyMigration.migrateToPrivacy(
                        context = context,
                        amountToMigrate = amountToMigrate,
                        organizer = organizer
                    )
                }
                is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                    transactionRepository.createAccounts(
                        organizer = organizer
                    )
                }
            }
        }
    }
}