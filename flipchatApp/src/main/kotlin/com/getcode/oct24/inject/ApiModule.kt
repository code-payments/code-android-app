package com.getcode.oct24.inject

import android.content.Context
import com.getcode.oct24.util.AccountAuthenticator
import com.getcode.analytics.AnalyticsService
import com.getcode.services.manager.MnemonicManager
import com.getcode.model.Currency
import com.getcode.network.BalanceController
import com.getcode.network.PrivacyMigration
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.AccountService
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
import com.getcode.network.service.ChatServiceV1
import com.getcode.network.service.ChatServiceV2
import com.getcode.network.service.CurrencyService
import com.getcode.network.service.DeviceService
import com.getcode.oct24.BuildConfig
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.Kin
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

    @Singleton
    @Provides
    fun provideCompositeDisposable(): CompositeDisposable {
        return CompositeDisposable()
    }

    @Provides
    fun provideScheduler(): Scheduler = Schedulers.io()

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
        currencyUtils: CurrencyUtils,
    ): BalanceController {
        return BalanceController(
            exchange = exchange,
            balanceRepository = balanceRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            privacyMigration = privacyMigration,
            transactionReceiver = transactionReceiver,
            networkObserver = networkObserver,
            getCurrencyFromCode = { it?.name?.let(currencyUtils::getCurrency) },
            suffix = { _ -> resources.getKinSuffix() }
        )
    }

    @Singleton
    @Provides
    fun providesExchange(
        currencyService: CurrencyService,
        locale: LocaleHelper,
        currencyUtils: CurrencyUtils,
        prefRepository: PrefRepository,
    ): Exchange = CodeExchange(
        currencyService = currencyService,
        prefs = prefRepository,
        preferredCurrency = { Currency.Kin },
        defaultCurrency = { Currency.Kin }
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