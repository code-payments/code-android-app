package com.getcode.inject

import android.content.Context
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.analytics.AnalyticsService
import com.getcode.model.Currency
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.PrivacyMigration
import com.getcode.network.api.CurrencyApi
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
import com.getcode.util.AccountAuthenticator
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.network.service.ChatService
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    //@Named("unauthenticatedManagedChannel")
    fun provideUnauthenticatedManagedChannel(@ApplicationContext context: Context): ManagedChannel {
        val TLS_PORT = 443
        val DEV_URL = "api.codeinfra.dev"
        val PROD_URL = "api.codeinfra.net"

        return AndroidChannelBuilder.usingBuilder(
            OkHttpChannelBuilderForcedTls12.forAddress(
                PROD_URL,
                TLS_PORT
            )
        )
            .context(context)
            .userAgent("Code/Android/${BuildConfig.VERSION_NAME}")
            .keepAliveTime(4, TimeUnit.MINUTES)
            .build()
    }

    @Singleton
    @Provides
    fun provideAccountAuthenticator(@ApplicationContext context: Context): AccountAuthenticator {
        return AccountAuthenticator(context)
    }

    @Singleton
    @Provides
    fun provideMixpanelApi(@ApplicationContext context: Context): MixpanelAPI {
        return MixpanelAPI.getInstance(context, BuildConfig.MIXPANEL_API_KEY)
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
        @ApplicationContext context: Context,
        balanceRepository: BalanceRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        privacyMigration: PrivacyMigration,
        transactionReceiver: TransactionReceiver,
        networkObserver: NetworkConnectivityListener,
        locale: LocaleHelper,
        resources: ResourceHelper,
        currencyUtils: CurrencyUtils,
    ): BalanceController {
        return BalanceController(
            exchange = exchange,
            context = context,
            balanceRepository = balanceRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            privacyMigration = privacyMigration,
            transactionReceiver = transactionReceiver,
            networkObserver = networkObserver,
            getCurrency = { rates ->
                withContext(Dispatchers.Default) {
                    val defaultCurrencyCode = locale.getDefaultCurrency()?.code
                    return@withContext currencyUtils.getCurrenciesWithRates(rates)
                        .firstOrNull { p ->
                            p.code == defaultCurrencyCode
                        } ?: Currency.Kin
                }
            },
            getDefaultCountry = {
                locale.getDefaultCountry()
            },
            suffix = { resources.getString(R.string.core_ofKin) }
        )
    }

    @Singleton
    @Provides
    fun providesExchange(
        currencyApi: CurrencyApi,
        networkOracle: NetworkOracle,
        locale: LocaleHelper
    ): Exchange = CodeExchange(currencyApi, networkOracle) {
        locale.getDefaultCurrency()
    }

    @Singleton
    @Provides
    fun provideClient(
        @ApplicationContext context: Context,
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
    ): Client {
        return Client(
            context,
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