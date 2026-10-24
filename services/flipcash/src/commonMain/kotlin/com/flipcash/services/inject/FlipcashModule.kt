package com.flipcash.services.inject

import com.flipcash.services.user.AnalyticsIdentifier
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.annotations.FlipcashManagedStreamingChannel
import com.flipcash.services.internal.annotations.FlipcashProtocol
import com.flipcash.services.internal.domain.ActivityFeedMessageMapper
import com.flipcash.services.internal.domain.BlockedUserMapper
import com.flipcash.services.internal.domain.ContactMapper
import com.flipcash.services.internal.domain.ImageModerationResponseMapper
import com.flipcash.services.internal.domain.UserFlagsMapper
import com.flipcash.services.internal.domain.SocialAccountMapper
import com.flipcash.services.internal.domain.TextModerationResponseMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.BlobUploader
import com.flipcash.services.ForegroundGate
import com.flipcash.services.internal.network.HttpBlobUploader
import com.flipcash.services.internal.network.services.AccountService
import com.flipcash.services.internal.network.services.ActivityFeedService
import com.flipcash.services.internal.network.services.BlobStorageService
import com.flipcash.services.internal.network.services.BlocklistService
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.internal.network.services.EventStreamingService
import com.flipcash.services.internal.network.services.ChatMessagingService
import com.flipcash.services.internal.network.services.EmailVerificationService
import com.flipcash.services.internal.network.services.ContactListService
import com.flipcash.services.internal.network.services.ModerationService
import com.flipcash.services.internal.network.services.PollService
import com.flipcash.services.internal.repositories.InternalPollRepository
import com.flipcash.services.repository.PollRepository
import com.flipcash.services.internal.network.services.PhoneVerificationService
import com.flipcash.services.internal.network.services.ProfileService
import com.flipcash.services.internal.network.services.PurchaseService
import com.flipcash.services.internal.network.services.PushService
import com.flipcash.services.internal.network.services.CommunityService
import com.flipcash.services.internal.network.services.ResolverService
import com.flipcash.services.internal.network.services.ReportingService
import com.flipcash.services.internal.network.services.SettingsService
import com.flipcash.services.internal.network.services.ThirdPartyService
import com.flipcash.services.internal.repositories.InternalAccountRepository
import com.flipcash.services.internal.repositories.InternalActivityFeedRepository
import com.flipcash.services.internal.repositories.InternalBlobStorageRepository
import com.flipcash.services.internal.repositories.InternalBlocklistRepository
import com.flipcash.services.internal.repositories.InternalChatRepository
import com.flipcash.services.internal.repositories.InternalEventStreamingRepository
import com.flipcash.services.internal.repositories.InternalChatMessagingRepository
import com.flipcash.services.internal.repositories.InternalContactListRepository
import com.flipcash.services.internal.repositories.InternalContactVerificationRepository
import com.flipcash.services.internal.repositories.InternalModerationRepository
import com.flipcash.services.internal.repositories.InternalProfileRepository
import com.flipcash.services.internal.repositories.InternalPurchaseRepository
import com.flipcash.services.internal.repositories.InternalPushRepository
import com.flipcash.services.internal.repositories.InternalCommunityRepository
import com.flipcash.services.internal.repositories.InternalResolverRepository
import com.flipcash.services.internal.repositories.InternalReportingRepository
import com.flipcash.services.internal.repositories.InternalSettingsRepository
import com.flipcash.services.internal.repositories.InternalThirdPartyRepository
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.ActivityFeedRepository
import com.flipcash.services.repository.BlocklistRepository
import com.flipcash.services.repository.ChatRepository
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.repository.ChatMessagingRepository
import com.flipcash.services.repository.ContactListRepository
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.repository.ModerationRepository
import com.flipcash.services.repository.ProfileRepository
import com.flipcash.services.repository.PurchaseRepository
import com.flipcash.services.repository.PushRepository
import com.flipcash.services.repository.BlobStorageRepository
import com.flipcash.services.repository.CommunityRepository
import com.flipcash.services.repository.ResolverRepository
import com.flipcash.services.repository.ReportingRepository
import com.flipcash.services.repository.SettingsRepository
import com.flipcash.services.repository.ThirdPartyRepository
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.ManagedChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FlipcashModule {
    @Provides
    @JvmName("providesChatRepository")
    internal fun providesChatRepository(
        service: ChatService,
        mapper: ChatMetadataMapper,
        userProfileMapper: UserProfileMapper,
    ): ChatRepository = InternalChatRepository(service, mapper, userProfileMapper)

    @Provides
    @JvmName("providesEventStreamingRepository")
    internal fun providesEventStreamingRepository(
        service: EventStreamingService,
    ): EventStreamingRepository = InternalEventStreamingRepository(service)

    @Provides
    @JvmName("providesChatMessagingRepository")
    internal fun providesChatMessagingRepository(
        service: ChatMessagingService,
    ): ChatMessagingRepository = InternalChatMessagingRepository(service)

    @Provides
    @JvmName("providesContactListRepository")
    internal fun providesContactListRepository(
        service: ContactListService,
        contactMapper: ContactMapper,
    ): ContactListRepository = InternalContactListRepository(service, contactMapper)

    @Provides
    @JvmName("providesCommunityRepository")
    internal fun providesCommunityRepository(
        service: CommunityService,
        chatMapper: ChatMetadataMapper,
        userProfileMapper: UserProfileMapper,
    ): CommunityRepository = InternalCommunityRepository(service, chatMapper, userProfileMapper)

    @Provides
    @JvmName("providesResolverRepository")
    internal fun providesResolverRepository(
        service: ResolverService,
    ): ResolverRepository = InternalResolverRepository(service)

    @Provides
    @JvmName("providesBlobStorageRepository")
    internal fun providesBlobStorageRepository(
        service: BlobStorageService,
    ): BlobStorageRepository = InternalBlobStorageRepository(service)

    @Provides
    @JvmName("providesBlobUploader")
    internal fun providesBlobUploader(
        uploader: HttpBlobUploader,
    ): BlobUploader = uploader

    @Provides
    @JvmName("providesAccountRepository")
    internal fun providesAccountRepository(
        service: AccountService,
        mapper: UserFlagsMapper,
    ): AccountRepository = InternalAccountRepository(service, mapper)

    @Provides
    @JvmName("providesActivityFeedRepository")
    internal fun providesActivityFeedRepository(
        service: ActivityFeedService,
        mapper: ActivityFeedMessageMapper,
    ): ActivityFeedRepository = InternalActivityFeedRepository(service, mapper)

    @Provides
    @JvmName("providesBlocklistRepository")
    internal fun providesBlocklistRepository(
        service: BlocklistService,
        mapper: BlockedUserMapper,
    ): BlocklistRepository = InternalBlocklistRepository(service, mapper)

    @Provides
    @JvmName("providesPurchaseRepository")
    internal fun providesPurchaseRepository(
        service: PurchaseService,
    ): PurchaseRepository = InternalPurchaseRepository(service)

    @Provides
    @JvmName("providesPushRepository")
    internal fun providesPushRepository(
        service: PushService,
    ): PushRepository = InternalPushRepository(service)

    @Provides
    @JvmName("providesSettingsRepository")
    internal fun providesSettingsRepository(
        service: SettingsService
    ): SettingsRepository = InternalSettingsRepository(service)

    @Provides
    @JvmName("providesThirdPartyRepository")
    internal fun providesThirdPartyRepository(
        service: ThirdPartyService,
    ): ThirdPartyRepository = InternalThirdPartyRepository(service)

    @Provides
    @JvmName("providesContactVerificationRepository")
    internal fun providesContactVerificationRepository(
        emailService: EmailVerificationService,
        phoneService: PhoneVerificationService,
    ): ContactVerificationRepository =
        InternalContactVerificationRepository(emailService, phoneService)

    @Provides
    @JvmName("providesProfileRepository")
    internal fun providesProfileRepository(
        service: ProfileService,
        userProfileMapper: UserProfileMapper,
        socialAccountMapper: SocialAccountMapper,
    ): ProfileRepository =
        InternalProfileRepository(service, userProfileMapper, socialAccountMapper)

    @Provides
    @JvmName("providesPollRepository")
    internal fun providesPollRepository(
        service: PollService,
    ): PollRepository = InternalPollRepository(service)

    @Provides
    @JvmName("providesModerationRepository")
    internal fun providesModerationRepository(
        service: ModerationService,
        textModerationResponseMapper: TextModerationResponseMapper,
        imageModerationResponseMapper: ImageModerationResponseMapper,
    ): ModerationRepository = InternalModerationRepository(
        service,
        textModerationResponseMapper,
        imageModerationResponseMapper
    )

    @Provides
    @JvmName("providesReportingRepository")
    internal fun providesReportingRepository(
        service: ReportingService,
    ): ReportingRepository = InternalReportingRepository(service)
}
