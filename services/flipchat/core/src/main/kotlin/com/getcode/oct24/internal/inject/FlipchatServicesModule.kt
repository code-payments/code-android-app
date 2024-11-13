package com.getcode.oct24.internal.inject

import com.getcode.network.BalanceController
import com.getcode.network.PrivacyMigration
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.oct24.user.UserManager
import com.getcode.services.annotations.EcdsaLookup
import com.getcode.services.db.CurrencyProvider
import com.getcode.services.model.EcdsaTuple
import com.getcode.services.model.EcdsaTupleQuery
import com.getcode.services.network.core.NetworkOracle
import com.getcode.services.network.core.NetworkOracleImpl
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FlipchatServicesModule {

    @Provides
    @EcdsaLookup
    fun providesEcdsaTuple(
        userManager: UserManager
    ): EcdsaTupleQuery {
        return {
            EcdsaTuple(userManager.keyPair, userManager.userId)
        }
    }

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

    @Singleton
    @Provides
    fun provideBalanceController(
        @EcdsaLookup lookup: EcdsaTupleQuery,
        userManager: UserManager,
        exchange: Exchange,
        balanceRepository: BalanceRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        privacyMigration: PrivacyMigration,
        transactionReceiver: TransactionReceiver,
        networkObserver: NetworkConnectivityListener,
        currencyUtils: CurrencyUtils,
        currencyProvider: CurrencyProvider,
    ): BalanceController {
        return BalanceController(
            storedEcda = lookup,
            exchange = exchange,
            balanceRepository = balanceRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            privacyMigration = privacyMigration,
            transactionReceiver = transactionReceiver,
            networkObserver = networkObserver,
            getCurrencyFromCode = {
                it?.name?.let(currencyUtils::getCurrency)
            },
            onOrganizerUpdated = { userManager.set(organizer = it) },
            organizerLookup = { userManager.organizer },
            suffix = { currency -> currencyProvider.suffix(currency) }
        )
    }

}