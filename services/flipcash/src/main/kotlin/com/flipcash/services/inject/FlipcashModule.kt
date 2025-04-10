package com.flipcash.services.inject

import android.content.Context
import com.flipcash.services.billing.BillingClient
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.annotations.FlipcashProtocol
import com.flipcash.services.internal.billing.GooglePlayBillingClient
import com.flipcash.services.internal.domain.UserFlagsMapper
import com.flipcash.services.internal.network.services.AccountService
import com.flipcash.services.internal.network.services.PurchaseService
import com.flipcash.services.internal.network.services.PushService
import com.flipcash.services.internal.repositories.InternalAccountRepository
import com.flipcash.services.internal.repositories.InternalPurchaseRepository
import com.flipcash.services.internal.repositories.InternalPushRepository
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.PurchaseRepository
import com.flipcash.services.repository.PushRepository
import com.flipcash.services.user.UserManager
import com.getcode.libs.logging.BuildConfig
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.annotations.OpenCodeProtocol
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
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
internal object FlipcashModule {
    @Singleton
    @OpenCodeProtocol
    @Provides
    fun providesOpenCodeProtocolConfig(): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = "payments.api.flipcash-infra.com" // TODO: swap for flipcash
            override val userAgent: String
                get() = "Flipcash/Payments/Android/1.0.0" // TODO: Feed in app version

        }
    }

    @Singleton
    @FlipcashProtocol
    @Provides
    fun providesFlipcashProtocolConfig(): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = "api.flipcash-infra.com" // TODO: swap for flipcash
            override val userAgent: String
                get() = "Flipcash/Core/Android/1.0.0"  // TODO: Feed in app version

        }
    }

    @Singleton
    @Provides
    @FlipcashManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        @FlipcashProtocol
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
        service: AccountService,
        mapper: UserFlagsMapper,
    ): AccountRepository = InternalAccountRepository(service, mapper)

    @Provides
    internal fun providesPurchaseRepository(
        service: PurchaseService,
    ): PurchaseRepository = InternalPurchaseRepository(service)

    @Provides
    internal fun providesPushRepository(
        service: PushService,
    ): PushRepository = InternalPushRepository(service)

    @Provides
    internal fun providesBillingClient(
        @ApplicationContext context: Context,
        repository: PurchaseRepository,
        userManager: UserManager,
    ): BillingClient = GooglePlayBillingClient(context, userManager, repository)
}