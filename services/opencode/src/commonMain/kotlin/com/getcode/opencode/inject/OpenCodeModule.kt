package com.getcode.opencode.inject

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.domain.repositories.InternalAccountRepository
import com.getcode.opencode.internal.domain.repositories.InternalBalanceRepository
import com.getcode.opencode.internal.domain.repositories.InternalCurrencyRepository
import com.getcode.opencode.internal.domain.repositories.InternalEventRepository
import com.getcode.opencode.internal.domain.repositories.InternalMessagingRepository
import com.getcode.opencode.internal.domain.repositories.InternalSwapRepository
import com.getcode.opencode.internal.domain.repositories.InternalTransactionRepository
import com.getcode.opencode.internal.exchange.OpenCodeExchange
import com.getcode.opencode.internal.exchange.RealVerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.network.pollers.SwapPoller
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.internal.network.services.BalanceService
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.opencode.repositories.EventRepository
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.hoc081098.channeleventbus.ChannelEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@InstallIn(SingletonComponent::class)
@Module
object OpenCodeModule {
    @Provides
    @Singleton
    @JvmName("providesExchange")
    internal fun providesExchange(
        currencyController: CurrencyController,
        verifiedStateManager: VerifiedProtoManager,
        resources: ResourceHelper,
        locale: LocaleHelper,
    ): Exchange = OpenCodeExchange(
        currencyController = currencyController,
        verifiedStateManager = verifiedStateManager,
        resources = resources,
        locale = locale,
    )


    @Provides
    @Singleton
    @JvmName("providesAccountRepository")
    internal fun providesAccountRepository(
        service: AccountService,
        transactionService: TransactionService,
    ): AccountRepository = InternalAccountRepository(transactionService, service)

    @Provides
    @Singleton
    @JvmName("providesMessagingRepository")
    internal fun providesMessagingRepository(
        service: MessagingService
    ): MessagingRepository = InternalMessagingRepository(service)

    @Provides
    @Singleton
    @JvmName("providesTransactionRepository")
    internal fun providesTransactionRepository(
        service: TransactionService,
    ): TransactionRepository = InternalTransactionRepository(service)

    @Provides
    @Singleton
    @JvmName("providesSwapRepository")
    internal fun providesSwapRepository(
        service: SwapService,
        swapPoller: SwapPoller,
    ): SwapRepository = InternalSwapRepository(service, swapPoller)

    @Provides
    @Singleton
    @JvmName("providesEventRepository")
    internal fun providesEventRepository(
        eventBus: ChannelEventBus,
        accountController: AccountController,
        transactionController: TransactionController,
        sessionListeners: @JvmSuppressWildcards Set<SessionListener>,
    ): EventRepository = InternalEventRepository(eventBus, accountController, transactionController, sessionListeners)

    @Provides
    @Singleton
    @JvmName("providesCurrencyRepository")
    internal fun providesCurrencyRepository(
        service: CurrencyService
    ): CurrencyRepository = InternalCurrencyRepository(service)

    @Provides
    @Singleton
    @JvmName("providesBalanceRepository")
    internal fun providesBalanceRepository(
        service: BalanceService
    ): BalanceRepository = InternalBalanceRepository(service)

    @Provides
    @Singleton
    @JvmName("providesEventBus")
    internal fun providesEventBus(): ChannelEventBus = ChannelEventBus()

    @Provides
    @Singleton
    fun bindTransactionOperations(impl: TransactionController): TransactionOperations = impl

    @Provides
    @Singleton
    @JvmName("bindVerifiedFiatCalculator")
    internal fun bindVerifiedFiatCalculator(impl: RealVerifiedFiatCalculator): VerifiedFiatCalculator = impl
}