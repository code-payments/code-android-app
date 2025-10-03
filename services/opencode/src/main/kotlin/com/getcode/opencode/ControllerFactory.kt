package com.getcode.opencode

import android.content.Context
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.inject.OpenCodeModule
import dagger.hilt.android.EntryPointAccessors

object ControllerFactory {
    fun createAccountController(context: Context, config: ProtocolConfig): AccountController {
        return AccountController(
            accountRepository = RepositoryFactory.createAccountRepository(context, config),
            transactionController = createTransactionController(context, config),
            networkObserver = NetworkFactory.createNetworkObserver(context),
        )
    }

    fun createBalanceController(context: Context,config: ProtocolConfig): BalanceController {
        return BalanceController(
            accountController = createAccountController(context, config),
            networkObserver = NetworkFactory.createNetworkObserver(context),
        )
    }

    fun createMessagingController(context: Context, config: ProtocolConfig): MessagingController {
        return MessagingController(RepositoryFactory.createMessagingRepository(context, config))
    }

    fun createTransactionController(context: Context, config: ProtocolConfig): TransactionController {
        val appContext = context.applicationContext ?: throw IllegalStateException(
            "applicationContext was not provided",
        )

        val module =  EntryPointAccessors.fromApplication(
            appContext,
            OpenCodeModule::class.java,
        )

        return TransactionController(
            repository = RepositoryFactory.createTransactionRepository(context, config),
            eventBus = module.providesEventBus(),
        )
    }
    fun createCurrencyController(context: Context, config: ProtocolConfig): CurrencyController {
        return CurrencyController(
            repository = RepositoryFactory.createCurrencyRepository(context, config),
        )
    }

    fun createTokenController(context: Context, config: ProtocolConfig): TokenController {
        return TokenController(
            accountController = createAccountController(context, config),
            balanceController = createBalanceController(context, config),
            currencyController = createCurrencyController(context, config),
            networkObserver = NetworkFactory.createNetworkObserver(context),
        )
    }
}