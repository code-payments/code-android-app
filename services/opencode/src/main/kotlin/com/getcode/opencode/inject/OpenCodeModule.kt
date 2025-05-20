package com.getcode.opencode.inject

import android.content.Context
import com.getcode.libs.logging.BuildConfig
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeProtocol
import com.getcode.opencode.internal.domain.repositories.InternalAccountRepository
import com.getcode.opencode.internal.domain.repositories.InternalEventRepository
import com.getcode.opencode.internal.domain.repositories.InternalMessagingRepository
import com.getcode.opencode.internal.domain.repositories.InternalTransactionRepository
import com.getcode.opencode.internal.exchange.OpenCodeExchange
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.EventRepository
import com.getcode.opencode.repositories.MessagingRepository
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
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import org.kin.sdk.base.network.api.agora.OkHttpChannelBuilderForcedTls12
import java.util.concurrent.TimeUnit
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

    @Singleton
    @OpenCodeProtocol
    @Provides
    fun providesOpenCodeProtocolConfig(
        @ApplicationContext context: Context
    ): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = "ocp.api.flipcash-infra.net"
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
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveTime(config.keepAlive.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .keepAliveTimeout(config.keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    this.intercept(LoggingClientInterceptor())
                }
            }
            .build()
    }

    @Provides
    @Singleton
    internal fun providesAccountRepository(
        service: AccountService
    ): AccountRepository = InternalAccountRepository(service)

    @Provides
    @Singleton
    internal fun providesMessagingRepository(
        service: MessagingService
    ): MessagingRepository = InternalMessagingRepository(service)

    @Provides
    @Singleton
    internal fun providesTransactionRepository(
        service: TransactionService
    ): TransactionRepository = InternalTransactionRepository(service)

    @Provides
    @Singleton
    internal fun providesEventRepository(
        eventBus: ChannelEventBus,
        balanceController: BalanceController,
        transactionController: TransactionController,
    ): EventRepository = InternalEventRepository(eventBus, balanceController, transactionController)

    @Provides
    @Singleton
    internal fun providesEventBus(): ChannelEventBus = ChannelEventBus()
}