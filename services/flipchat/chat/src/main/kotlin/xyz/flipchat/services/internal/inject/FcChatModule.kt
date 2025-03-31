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
import xyz.flipchat.services.FcChatConfig
import xyz.flipchat.services.chat.BuildConfig
import xyz.flipchat.services.internal.annotations.ChatManagedChannel
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FcChatModule {

    @Singleton
    @Provides
    fun providesChatServicesConfig(): FcChatConfig {
        return FcChatConfig()
    }

    @Singleton
    @Provides
    @ChatManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        config: FcChatConfig,
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
}