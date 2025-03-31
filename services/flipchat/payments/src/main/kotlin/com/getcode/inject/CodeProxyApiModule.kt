package com.getcode.inject

import com.getcode.network.exchange.CodeExchange
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.service.CurrencyService
import com.getcode.services.db.CurrencyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}