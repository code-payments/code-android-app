package com.getcode.opencode.inject

import android.content.Context
import com.getcode.libs.logging.BuildConfig
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.internal.annotations.OpenCodeProtocol
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.domain.repositories.InternalAccountRepository
import com.getcode.opencode.internal.domain.repositories.InternalBalanceRepository
import com.getcode.opencode.internal.domain.repositories.InternalMessagingRepository
import com.getcode.opencode.internal.domain.repositories.InternalTransactionRepository
import com.getcode.opencode.internal.exchange.OpenCodeExchange
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.internal.network.core.NetworkOracleImpl
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
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

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

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
            .apply {
                if (BuildConfig.DEBUG) {
                    this.intercept(LoggingClientInterceptor())
                }
            }
            .build()
    }

    @Provides
    internal fun providesAccountRepository(
        service: AccountService
    ): AccountRepository = InternalAccountRepository(service)

    @Provides
    internal fun providesBalanceRepository(
        exchange: Exchange,
        networkObserver: NetworkConnectivityListener,
        accountService: AccountService,
        transactionService: TransactionService,
    ): BalanceRepository = InternalBalanceRepository(
        exchange = exchange,
        networkObserver = networkObserver,
        accountService = accountService,
        transactionService = transactionService
    )

    @Provides
    internal fun providesMessagingRepository(
        service: MessagingService
    ): MessagingRepository = InternalMessagingRepository(service)

    @Provides
    internal fun providesTransactionRepository(
        service: TransactionService
    ): TransactionRepository = InternalTransactionRepository(service)
}