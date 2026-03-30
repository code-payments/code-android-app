package com.getcode.opencode.inject

import android.content.Context
import com.getcode.libs.logging.BuildConfig
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeManagedStreamingChannel
import com.getcode.opencode.internal.annotations.OpenCodeProtocol
import com.getcode.opencode.internal.domain.repositories.InternalAccountRepository
import com.getcode.opencode.internal.domain.repositories.InternalCurrencyRepository
import com.getcode.opencode.internal.domain.repositories.InternalEventRepository
import com.getcode.opencode.internal.domain.repositories.InternalMessagingRepository
import com.getcode.opencode.internal.domain.repositories.InternalSwapRepository
import com.getcode.opencode.internal.domain.repositories.InternalTransactionRepository
import com.getcode.opencode.internal.exchange.OpenCodeExchange
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.network.pollers.SwapPoller
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.opencode.repositories.EventRepository
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.hoc081098.channeleventbus.ChannelEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@InstallIn(SingletonComponent::class)
@Module
object OpenCodeModule {
    @Provides
    @Singleton
    internal fun providesExchange(
        currencyController: CurrencyController,
        resources: ResourceHelper,
        locale: LocaleHelper,
    ): Exchange = OpenCodeExchange(
        currencyController = currencyController,
        resources = resources,
        locale = locale,
    )

    @Singleton
    @OpenCodeProtocol
    @Provides
    fun providesOpenCodeProtocolConfig(
        @ApplicationContext context: Context
    ): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = "ocp-v2.api.flipcash-infra.net"
            override val userAgent: String
                get() {
                    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    return "OpenCodeProtocol/Android/$version"
                }
        }
    }

    @Singleton
    @Provides
    @OpenCodeManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel {
        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilder.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveWithoutCalls(false)
            .idleTimeout(5, TimeUnit.MINUTES) // drop idle connections
            .apply {
                if (BuildConfig.DEBUG) {
                    this.intercept(LoggingClientInterceptor())
                }
            }
            .build()
            .also { observeChannelState("opencode", it) }
    }

    @Singleton
    @Provides
    @OpenCodeManagedStreamingChannel
    fun provideManagedStreamingChannel(
        @ApplicationContext context: Context,
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel {
        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilder.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveWithoutCalls(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    this.intercept(LoggingClientInterceptor())
                }
            }
            .build()
            .also { observeChannelState("opencode-stream", it) }
    }

    private fun observeChannelState(name: String, channel: ManagedChannel) {
        val state = channel.getState(false)
        trace(
            tag = "gRPC",
            message = "$name => $state",
            type = TraceType.StateChange,
        )
        if (state != ConnectivityState.SHUTDOWN) {
            channel.notifyWhenStateChanged(state) {
                observeChannelState(name, channel)
            }
        }
    }

    @Provides
    @Singleton
    internal fun providesAccountRepository(
        service: AccountService,
        transactionService: TransactionService,
    ): AccountRepository = InternalAccountRepository(transactionService, service)

    @Provides
    @Singleton
    internal fun providesMessagingRepository(
        service: MessagingService
    ): MessagingRepository = InternalMessagingRepository(service)

    @Provides
    @Singleton
    internal fun providesTransactionRepository(
        service: TransactionService,
    ): TransactionRepository = InternalTransactionRepository(service)

    @Provides
    @Singleton
    internal fun providesSwapRepository(
        service: SwapService,
        swapPoller: SwapPoller,
    ): SwapRepository = InternalSwapRepository(service, swapPoller)

    @Provides
    @Singleton
    internal fun providesEventRepository(
        eventBus: ChannelEventBus,
        accountController: AccountController,
        transactionController: TransactionController,
        sessionListeners: @JvmSuppressWildcards Set<SessionListener>,
    ): EventRepository = InternalEventRepository(eventBus, accountController, transactionController, sessionListeners)

    @Provides
    @Singleton
    internal fun providesCurrencyRepository(
        service: CurrencyService
    ): CurrencyRepository = InternalCurrencyRepository(service)
    @Provides
    @Singleton
    internal fun providesEventBus(): ChannelEventBus = ChannelEventBus()
}