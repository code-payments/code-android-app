package com.getcode.opencode.inject

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.exchange.OpenCodeExchange
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object OpenCodeModule {
    @Provides
    @Singleton
    internal fun providesExchange(
        currencyService: CurrencyService,
        resources: ResourceHelper,
        locale: LocaleHelper,
    ): Exchange = OpenCodeExchange(
        currencyService = currencyService,
        resources = resources,
        locale = locale,
    )
}