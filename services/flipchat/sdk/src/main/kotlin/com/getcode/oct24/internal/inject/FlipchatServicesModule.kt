package com.getcode.oct24.internal.inject

import com.getcode.network.BalanceController
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.Client
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.MessagingRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.services.db.CurrencyProvider
import com.getcode.services.network.core.NetworkOracle
import com.getcode.services.network.core.NetworkOracleImpl
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.flipchat.services.user.UserManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FlipchatServicesModule {

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

    @Provides
    fun providesOrganizerLookup(userManager: UserManager): () -> com.getcode.solana.organizer.Organizer? {
        return { userManager.organizer }
    }

    @Singleton
    @Provides
    fun provideClient(
        userManager: UserManager,
        transactionRepository: TransactionRepository,
        messagingRepository: MessagingRepository,
        accountRepository: AccountRepository,
        balanceController: BalanceController,
        transactionReceiver: TransactionReceiver,
        exchange: Exchange,
        networkObserver: NetworkConnectivityListener,
    ): Client {
        return Client(
            userManager = userManager,
            transactionRepository,
            messagingRepository,
            balanceController,
            accountRepository,
            exchange,
            transactionReceiver,
            networkObserver,
        )
    }

    @Singleton
    @Provides
    fun provideBalanceController(
        userManager: UserManager,
        exchange: Exchange,
        balanceRepository: BalanceRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        transactionReceiver: TransactionReceiver,
        networkObserver: NetworkConnectivityListener,
        currencyUtils: CurrencyUtils,
        currencyProvider: CurrencyProvider,
    ): BalanceController {
        return BalanceController(
            userManager = userManager,
            exchange = exchange,
            balanceRepository = balanceRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            transactionReceiver = transactionReceiver,
            networkObserver = networkObserver,
            getCurrencyFromCode = {
                it?.name?.let(currencyUtils::getCurrency)
            },
            suffix = { currency -> currencyProvider.suffix(currency) }
        )
    }

    @Singleton
    @Provides
    fun provideTransactionRepository(
        transactionApi: TransactionApiV2,
    ): TransactionRepository {
        return TransactionRepository(transactionApi)
    }

}