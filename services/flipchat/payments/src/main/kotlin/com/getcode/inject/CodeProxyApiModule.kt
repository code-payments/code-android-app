package com.getcode.inject

import android.content.Context
import com.getcode.network.BalanceController
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.Client
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.CodeExchange
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.MessagingRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.service.CurrencyService
import com.getcode.services.analytics.AnalyticsService
import com.getcode.services.db.CurrencyProvider
import com.getcode.services.manager.MnemonicManager
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CodeProxyApiModule {

    @Singleton
    @Provides
    fun providesExchange(
        currencyService: CurrencyService,
        currencyProvider: CurrencyProvider,
    ): Exchange = CodeExchange(
        currencyService = currencyService,
        preferredCurrency = { currencyProvider.preferredCurrency() },
        defaultCurrency = { currencyProvider.defaultCurrency() }
    )

    @Singleton
    @Provides
    fun provideBalanceRepository(
    ): BalanceRepository {
        return BalanceRepository()
    }

    @Singleton
    @Provides
    fun provideTransactionRepository(
        @ApplicationContext context: Context,
        transactionApi: TransactionApiV2,
    ): TransactionRepository {
        return TransactionRepository(transactionApi = transactionApi, context = context)
    }
}