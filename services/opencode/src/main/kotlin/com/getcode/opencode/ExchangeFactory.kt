package com.getcode.opencode

import android.content.Context
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.inject.OpenCodeModule
import com.getcode.opencode.internal.domain.mapping.HistoricalMintDataMapper
import com.getcode.opencode.internal.domain.mapping.LaunchpadMetadataMapper
import com.getcode.opencode.internal.domain.mapping.MintMapper
import com.getcode.opencode.internal.domain.mapping.VmMetadataMapper
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.util.locale.LocaleModule
import com.getcode.util.resources.AndroidResources
import dagger.hilt.android.EntryPointAccessors

object ExchangeFactory {
    fun createOpenCodeExchange(context: Context, config: ProtocolConfig): Exchange {
        val appContext = context.applicationContext ?: throw IllegalStateException(
            "applicationContext was not provided",
        )

        val module = EntryPointAccessors.fromApplication(
            appContext,
            OpenCodeModule::class.java,
        )

        val localeModule =  EntryPointAccessors.fromApplication(
            appContext,
            LocaleModule::class.java,
        )


        val api = CurrencyApi(module.provideManagedChannel(context, config))
        val mintMapper = MintMapper(
            vmMetadataMapper = VmMetadataMapper(),
            launchpadMetadataMapper = LaunchpadMetadataMapper(),
        )
        val historicalMintDataMapper = HistoricalMintDataMapper()
        val locale = localeModule.bindLocaleHelper(context)
        val resources = AndroidResources(context)
        val service = CurrencyService(api, mintMapper, historicalMintDataMapper)
        return module.providesExchange(service, resources, locale)
    }
}