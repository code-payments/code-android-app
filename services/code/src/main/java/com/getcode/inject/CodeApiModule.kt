package com.getcode.inject

import android.content.Context
import com.getcode.CodeServicesConfig
import com.getcode.analytics.AnalyticsService
import com.getcode.analytics.AnalyticsServiceNull
import com.getcode.annotations.CodeManagedChannel
import com.getcode.libs.logging.BuildConfig
import com.getcode.network.BalanceController
import com.getcode.network.PrivacyMigration
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.Client
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.CodeExchange
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.MessagingRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.service.AccountService
import com.getcode.network.service.ChatService
import com.getcode.network.service.CurrencyService
import com.getcode.network.service.DeviceService
import com.getcode.services.db.CurrencyProvider
import com.getcode.services.manager.MnemonicManager
import com.getcode.services.network.core.NetworkOracle
import com.getcode.services.network.core.NetworkOracleImpl
import com.getcode.services.utils.logging.LoggingClientInterceptor
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.network.NetworkConnectivityListener
import com.mixpanel.android.mpmetrics.MixpanelAPI
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

@Module
@InstallIn(SingletonComponent::class)
internal object CodeApiModule {

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

    @Singleton
    @Provides
    fun providesServicesConfig(): CodeServicesConfig {
        return CodeServicesConfig()
    }

    @Singleton
    @Provides
    @CodeManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        config: CodeServicesConfig,
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

    @Singleton
    @Provides
    fun provideClient(
        identityRepository: IdentityRepository,
        transactionRepository: TransactionRepository,
        messagingRepository: MessagingRepository,
        accountRepository: AccountRepository,
        accountService: AccountService,
        balanceController: BalanceController,
        analytics: AnalyticsService,
        prefRepository: PrefRepository,
        transactionReceiver: TransactionReceiver,
        exchange: Exchange,
        networkObserver: NetworkConnectivityListener,
        chatService: ChatService,
        deviceService: DeviceService,
        mnemonicManager: MnemonicManager,
    ): Client {
        return Client(
            identityRepository,
            transactionRepository,
            messagingRepository,
            balanceController,
            accountRepository,
            accountService,
            analytics,
            prefRepository,
            exchange,
            transactionReceiver,
            networkObserver,
            chatService,
            deviceService,
            mnemonicManager
        )
    }

    @Singleton
    @Provides
    fun provideBalanceRepository(
    ): BalanceRepository {
        return BalanceRepository()
    }

    @Singleton
    @Provides
    fun provideBalanceController(
        exchange: Exchange,
        balanceRepository: BalanceRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        privacyMigration: PrivacyMigration,
        transactionReceiver: TransactionReceiver,
        networkObserver: NetworkConnectivityListener,
        currencyUtils: CurrencyUtils,
        currencyProvider: CurrencyProvider,
    ): BalanceController {
        return BalanceController(
            exchange = exchange,
            balanceRepository = balanceRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            privacyMigration = privacyMigration,
            transactionReceiver = transactionReceiver,
            networkObserver = networkObserver,
            getCurrencyFromCode = {
                it?.name?.let(currencyUtils::getCurrency)
            },
            suffix = { currency -> currencyProvider.suffix(currency) }
        )
    }

    @Singleton
    @Provides
    fun providesExchange(
        currencyService: CurrencyService,
        prefRepository: PrefRepository,
        currencyProvider: CurrencyProvider,
    ): Exchange = CodeExchange(
        currencyService = currencyService,
        prefs = prefRepository,
        preferredCurrency = { currencyProvider.preferredCurrency() },
        defaultCurrency = { currencyProvider.defaultCurrency() }
    )

    @Singleton
    @Provides
    fun providePrivacyMigration(
        transactionRepository: TransactionRepository,
        analytics: AnalyticsService,
    ): PrivacyMigration {
        return PrivacyMigration(
            transactionRepository,
            analytics
        )
    }

    @Singleton
    @Provides
    fun provideTransactionRepository(
        @ApplicationContext context: Context,
        transactionApi: TransactionApiV2,
    ): TransactionRepository {
        return TransactionRepository(transactionApi = transactionApi, context = context)
    }

    // TODO:
    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI
    ): AnalyticsService = AnalyticsServiceNull()
}