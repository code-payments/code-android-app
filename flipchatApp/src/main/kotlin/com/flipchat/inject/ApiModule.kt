package com.flipchat.inject

import android.content.Context
import com.flipchat.BuildConfig
import com.flipchat.util.AccountAuthenticator
import com.getcode.analytics.AnalyticsService
import com.getcode.annotations.DevManagedChannel
import com.getcode.manager.MnemonicManager
import com.getcode.model.CurrencyCode
import com.getcode.model.PrefsString
import com.getcode.network.BalanceController
import com.getcode.network.PrivacyMigration
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.AccountService
import com.getcode.network.client.Client
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.core.NetworkOracle
import com.getcode.network.core.NetworkOracleImpl
import com.getcode.network.exchange.CodeExchange
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.MessagingRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.service.ChatServiceV1
import com.getcode.network.service.ChatServiceV2
import com.getcode.network.service.CurrencyService
import com.getcode.network.service.DeviceService
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.kin.sdk.base.network.api.agora.OkHttpChannelBuilderForcedTls12
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

    @Singleton
    @Provides
    fun provideCompositeDisposable(): CompositeDisposable {
        return CompositeDisposable()
    }

    @Provides
    fun provideScheduler(): Scheduler = Schedulers.io()

    @Singleton
    @Provides
    fun provideManagedChannel(@ApplicationContext context: Context): ManagedChannel {
        val TLS_PORT = 443
        val PROD_URL = "api.codeinfra.net"

        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(PROD_URL, TLS_PORT))
            .context(context)
            .userAgent("Code/Android/${BuildConfig.VERSION_NAME}")
            .keepAliveTime(4, TimeUnit.MINUTES)
            .build()
    }

    @Singleton
    @Provides
    @DevManagedChannel
    fun provideDevManagedChannel(@ApplicationContext context: Context): ManagedChannel {
        val TLS_PORT = 443
        val DEV_URL = "api.codeinfra.dev"

        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(DEV_URL, TLS_PORT))
            .context(context)
            .userAgent("Code/Android/${BuildConfig.VERSION_NAME}")
            .keepAliveTime(4, TimeUnit.MINUTES)
            .build()
    }

    @Singleton
    @Provides
    fun provideAccountAuthenticator(
        @ApplicationContext context: Context,
    ): AccountAuthenticator {
        return AccountAuthenticator(context)
    }

    @Singleton
    @Provides
    fun provideMixpanelApi(@ApplicationContext context: Context): MixpanelAPI {
        return MixpanelAPI.getInstance(context, BuildConfig.MIXPANEL_API_KEY)
    }

    @Singleton
    @Provides
    fun providesHttpLoggingInterceptor() = HttpLoggingInterceptor()
        .apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Singleton
    @Provides
    fun providesOkHttpClient(httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(httpLoggingInterceptor)
            .build()

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
        networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
        resources: ResourceHelper,
        currencyUtils: com.getcode.utils.CurrencyUtils,
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
            suffix = { currency ->
                if (currency?.code != CurrencyCode.KIN.name) {
                    resources.getOfKinSuffix()
                } else {
                    ""
                }
            }
        )
    }

    @Singleton
    @Provides
    fun providesExchange(
        currencyService: CurrencyService,
        locale: LocaleHelper,
        currencyUtils: com.getcode.utils.CurrencyUtils,
        prefRepository: PrefRepository,
    ): Exchange = CodeExchange(
        currencyService = currencyService,
        prefs = prefRepository,
        preferredCurrency = {
            val preferredCurrencyCode = prefRepository.get(
                PrefsString.KEY_LOCAL_CURRENCY,
                ""
            ).takeIf { it.isNotEmpty() }

            val preferredCurrency = preferredCurrencyCode?.let { currencyUtils.getCurrency(it) }
            preferredCurrency ?: locale.getDefaultCurrency()
        },
        defaultCurrency = { locale.getDefaultCurrency() }
    )

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
        networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
        chatServiceV1: ChatServiceV1,
        chatServiceV2: ChatServiceV2,
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
            chatServiceV1,
            chatServiceV2,
            deviceService,
            mnemonicManager
        )
    }

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
}