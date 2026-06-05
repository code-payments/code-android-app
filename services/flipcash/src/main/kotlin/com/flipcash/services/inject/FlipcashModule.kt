package com.flipcash.services.inject

import android.content.Context
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.annotations.FlipcashProtocol
import com.flipcash.services.internal.domain.ActivityFeedMessageMapper
import com.flipcash.services.internal.domain.ImageModerationResponseMapper
import com.flipcash.services.internal.domain.UserFlagsMapper
import com.flipcash.services.internal.domain.SocialAccountMapper
import com.flipcash.services.internal.domain.TextModerationResponseMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.network.services.AccountService
import com.flipcash.services.internal.network.services.ActivityFeedService
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.internal.network.services.EventStreamingService
import com.flipcash.services.internal.network.services.ChatMessagingService
import com.flipcash.services.internal.network.services.EmailVerificationService
import com.flipcash.services.internal.network.services.ContactListService
import com.flipcash.services.internal.network.services.ModerationService
import com.flipcash.services.internal.network.services.PhoneVerificationService
import com.flipcash.services.internal.network.services.ProfileService
import com.flipcash.services.internal.network.services.PurchaseService
import com.flipcash.services.internal.network.services.PushService
import com.flipcash.services.internal.network.services.ResolverService
import com.flipcash.services.internal.network.services.SettingsService
import com.flipcash.services.internal.network.services.ThirdPartyService
import com.flipcash.services.internal.repositories.InternalAccountRepository
import com.flipcash.services.internal.repositories.InternalActivityFeedRepository
import com.flipcash.services.internal.repositories.InternalChatRepository
import com.flipcash.services.internal.repositories.InternalEventStreamingRepository
import com.flipcash.services.internal.repositories.InternalChatMessagingRepository
import com.flipcash.services.internal.repositories.InternalContactListRepository
import com.flipcash.services.internal.repositories.InternalContactVerificationRepository
import com.flipcash.services.internal.repositories.InternalModerationRepository
import com.flipcash.services.internal.repositories.InternalProfileRepository
import com.flipcash.services.internal.repositories.InternalPurchaseRepository
import com.flipcash.services.internal.repositories.InternalPushRepository
import com.flipcash.services.internal.repositories.InternalResolverRepository
import com.flipcash.services.internal.repositories.InternalSettingsRepository
import com.flipcash.services.internal.repositories.InternalThirdPartyRepository
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.ActivityFeedRepository
import com.flipcash.services.repository.ChatRepository
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.repository.ChatMessagingRepository
import com.flipcash.services.repository.ContactListRepository
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.repository.ModerationRepository
import com.flipcash.services.repository.ProfileRepository
import com.flipcash.services.repository.PurchaseRepository
import com.flipcash.services.repository.PushRepository
import com.flipcash.services.repository.ResolverRepository
import com.flipcash.services.repository.SettingsRepository
import com.flipcash.services.repository.ThirdPartyRepository
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FlipcashModule {
    @Singleton
    @FlipcashProtocol
    @Provides
    fun providesFlipcashProtocolConfig(
        @ApplicationContext context: Context
    ): ProtocolConfig {
        return object : ProtocolConfig {
            override val baseUrl: String
                get() = "fc-v2.api.flipcash-infra.net"
            override val userAgent: String
                get() {
                    val version =
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    return "Flipcash/Android/$version"
                }
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
            .usingBuilder(OkHttpChannelBuilder.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveTime(config.keepAlive.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .keepAliveTimeout(config.keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .keepAliveWithoutCalls(true)
            .intercept(LoggingClientInterceptor())
            .build()
            .also { observeChannelState("flipcash", it) }
    }

    private fun observeChannelState(name: String, channel: ManagedChannel) {
        val state = channel.getState(false)
        trace(
            tag = "gRPC",
            message = "$name => $state",
            type = TraceType.StateChange,
        )
        if (state != ConnectivityState.SHUTDOWN) {
            channel.notifyWhenStateChanged(state) {
                observeChannelState(name, channel)
            }
        }
    }

    @Provides
    internal fun providesChatRepository(
        service: ChatService,
        mapper: ChatMetadataMapper,
    ): ChatRepository = InternalChatRepository(service, mapper)

    @Provides
    internal fun providesEventStreamingRepository(
        service: EventStreamingService,
    ): EventStreamingRepository = InternalEventStreamingRepository(service)

    @Provides
    internal fun providesChatMessagingRepository(
        service: ChatMessagingService,
    ): ChatMessagingRepository = InternalChatMessagingRepository(service)

    @Provides
    internal fun providesContactListRepository(
        service: ContactListService,
    ): ContactListRepository = InternalContactListRepository(service)

    @Provides
    internal fun providesResolverRepository(
        service: ResolverService,
    ): ResolverRepository = InternalResolverRepository(service)

    @Provides
    internal fun providesAccountRepository(
        service: AccountService,
        mapper: UserFlagsMapper,
    ): AccountRepository = InternalAccountRepository(service, mapper)

    @Provides
    internal fun providesActivityFeedRepository(
        service: ActivityFeedService,
        mapper: ActivityFeedMessageMapper,
    ): ActivityFeedRepository = InternalActivityFeedRepository(service, mapper)

    @Provides
    internal fun providesPurchaseRepository(
        service: PurchaseService,
    ): PurchaseRepository = InternalPurchaseRepository(service)

    @Provides
    internal fun providesPushRepository(
        service: PushService,
    ): PushRepository = InternalPushRepository(service)

    @Provides
    internal fun providesSettingsRepository(
        service: SettingsService
    ): SettingsRepository = InternalSettingsRepository(service)

    @Provides
    internal fun providesThirdPartyRepository(
        service: ThirdPartyService,
    ): ThirdPartyRepository = InternalThirdPartyRepository(service)

    @Provides
    internal fun providesContactVerificationRepository(
        emailService: EmailVerificationService,
        phoneService: PhoneVerificationService,
    ): ContactVerificationRepository =
        InternalContactVerificationRepository(emailService, phoneService)

    @Provides
    internal fun providesProfileRepository(
        service: ProfileService,
        userProfileMapper: UserProfileMapper,
        socialAccountMapper: SocialAccountMapper,
    ): ProfileRepository =
        InternalProfileRepository(service, userProfileMapper, socialAccountMapper)

    @Provides
    internal fun providesModerationRepository(
        service: ModerationService,
        textModerationResponseMapper: TextModerationResponseMapper,
        imageModerationResponseMapper: ImageModerationResponseMapper,
    ): ModerationRepository = InternalModerationRepository(
        service,
        textModerationResponseMapper,
        imageModerationResponseMapper
    )
}