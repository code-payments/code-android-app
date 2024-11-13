package xyz.flipchat.services.internal.inject

import android.content.Context
import com.getcode.services.utils.logging.LoggingClientInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import org.kin.sdk.base.network.api.agora.OkHttpChannelBuilderForcedTls12
import xyz.flipchat.services.FcPaymentsConfig
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FcPaymentsModule {

    @Singleton
    @Provides
    fun providesPaymentsServicesConfig(): FcPaymentsConfig {
        return FcPaymentsConfig()
    }

    @Singleton
    @Provides
    @PaymentsManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        config: FcPaymentsConfig,
    ): ManagedChannel {
        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveTime(config.keepAlive.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .intercept(LoggingClientInterceptor())
            .build()
    }
}