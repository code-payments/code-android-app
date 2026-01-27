package com.getcode.opencode

import android.content.Context
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.inject.OpenCodeModule
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

        val localeModule = EntryPointAccessors.fromApplication(
            appContext,
            LocaleModule::class.java,
        )

        val locale = localeModule.bindLocaleHelper(context)
        val resources = AndroidResources(context)
        val controller = ControllerFactory.createCurrencyController(context, config)
        return module.providesExchange(controller, resources, locale)
    }
}