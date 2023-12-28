package com.getcode.util

import android.content.Context
import com.getcode.BuildConfig
import com.getcode.manager.AnalyticsManager
import com.getcode.network.BalanceController
import com.getcode.network.PrivacyMigration
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.client.Client
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.core.NetworkOracle
import com.getcode.network.core.NetworkOracleImpl
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.CurrencyRepository
import com.getcode.view.main.connectivity.ConnectionRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
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
        @ApplicationContext context: Context,
        balanceRepository: BalanceRepository,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        privacyMigration: PrivacyMigration,
        transactionReceiver: TransactionReceiver
    ): BalanceController {
        return BalanceController(
            context,
            balanceRepository,
            transactionRepository,
            accountRepository,
            privacyMigration,
            transactionReceiver,

            )
    }

    @Singleton
    @Provides
    fun provideClient(
        @ApplicationContext context: Context,
        transactionRepository: TransactionRepository,
        accountRepository: AccountRepository,
        balanceController: BalanceController,
        analyticsManager: AnalyticsManager,
        prefRepository: PrefRepository,
        currencyRepository: CurrencyRepository,
        transactionReceiver: TransactionReceiver,
        exchange: Exchange
    ): Client {
        return Client(
            context,
            transactionRepository,
            balanceController,
            accountRepository,
            analyticsManager,
            prefRepository,
            currencyRepository,
            exchange,
            transactionReceiver,
        )
    }

    @Singleton
    @Provides
    fun providePrivacyMigration(
        transactionRepository: TransactionRepository,
        analyticsManager: AnalyticsManager,
    ): PrivacyMigration {
        return PrivacyMigration(
            transactionRepository,
            analyticsManager
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

    @Singleton
    @Provides
    fun provideConnectionRepository(@ApplicationContext context: Context): ConnectionRepository {
        return ConnectionRepository(context)
    }

}