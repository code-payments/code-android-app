package com.flipcash.desktop

import com.flipcash.app.analytics.FlipcashAnalytics
import coil3.SingletonImageLoader
import coil3.PlatformContext
import com.flipcash.app.blob.FilePickedPhotos
import com.flipcash.app.blob.JvmUploadImageCache
import com.flipcash.app.blob.PickedPhotos
import com.flipcash.app.blob.UploadImageCache
import com.flipcash.shared.chat.AppForeground
import com.flipcash.shared.chat.ChatNotifications
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.app.persistence.sources.IncomingMessageOpener
import com.flipcash.services.chat.GroupKeyEnvelopeStore
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.GroupKeySource
import com.flipcash.services.controllers.ChatController
import com.flipcash.shared.chat.media.ChatMediaLocation
import com.flipcash.shared.chat.media.DirectoryChatMediaLocation
import com.flipcash.shared.chat.media.ImageLoaderSentImageCache
import com.flipcash.shared.chat.media.SentImageCache
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.storage.DirectoryPreferencesFiles
import com.flipcash.app.core.storage.PreferencesFiles
import com.flipcash.app.devicelink.KeyringSeedVault
import com.flipcash.app.devicelink.SeedVault
import com.flipcash.app.funding.PurchaseMethod
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.funding.PurchaseMethodMetadata
import com.flipcash.app.funding.PurchaseMethodSelection
import com.flipcash.app.funding.PurchaseMethodState
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsFiles
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.chatcipher.DefaultInviteCipher
import com.getcode.chatcipher.InviteCipher
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ComposeResourceHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkObserverStub
import com.getcode.libs.emojis.reactions.DataStoreRecentReactionsStore
import com.getcode.libs.emojis.reactions.RecentReactionsOwner
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import java.io.File
import java.util.Currency
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop implementations of the seams Android binds in its Hilt modules. Funding, analytics and
 * connectivity are stubs for this slice; the rest are real.
 */
@Module
object DesktopModule {
    /** Per-user data directory: the database, DataStore files and no-backup files. */
    internal val appDir: File by lazy {
        File(System.getProperty("user.home"), ".flipcash").apply { mkdirs() }
    }

    @Provides
    @Singleton
    fun seedVault(): SeedVault = KeyringSeedVault()

    @Provides
    @Singleton
    fun dispatchers(): DispatcherProvider = object : DispatcherProvider {
        override val Default: CoroutineDispatcher = Dispatchers.Default
        override val Main: CoroutineDispatcher = Dispatchers.Main
        override val IO: CoroutineDispatcher = Dispatchers.IO
    }

    // ChatCryptoModule provides these from its companion object, which Metro's Dagger interop
    // does not read from a precompiled module.
    @Provides
    fun chatCipher(): ChatCipher = DefaultChatCipher

    @Provides
    fun inviteCipher(): InviteCipher = DefaultInviteCipher

    @Provides
    fun analytics(): FlipcashAnalytics = FlipcashAnalytics.None

    @Provides
    @Singleton
    fun locale(): LocaleHelper = object : LocaleHelper {
        override fun getLanguageTag(): String = Locale.getDefault().toLanguageTag()
        override fun getDefaultCurrencyName(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
        override suspend fun getDefaultCountry(): String? =
            Locale.getDefault().country.takeIf { it.length == 2 }
    }

    @Provides
    @Singleton
    fun network(): NetworkConnectivityListener = NetworkObserverStub()

    @Provides
    @Singleton
    fun preferencesFiles(): PreferencesFiles = DirectoryPreferencesFiles(appDir)

    @Provides
    @Singleton
    fun userFlagsFiles(): UserFlagsFiles = object : UserFlagsFiles {
        override val noBackupDir: File = File(appDir, "no_backup").apply { mkdirs() }
        override fun dataStoreFile(name: String): File = File(appDir, "datastore/$name.preferences_pb")
    }

    // Photo upload: the image codec is bound by JvmBlobModule; these need to know where the app keeps files.
    @Provides
    @Singleton
    fun uploadImageCache(): UploadImageCache = JvmUploadImageCache(File(appDir, "upload_cache").apply { mkdirs() })

    @Provides
    @Singleton
    fun pickedPhotos(): PickedPhotos = FilePickedPhotos()

    @Provides
    @Singleton
    fun chatMediaLocation(): ChatMediaLocation = DirectoryChatMediaLocation(appDir)

    @Provides
    @Singleton
    fun sentImageCache(): SentImageCache =
        ImageLoaderSentImageCache { SingletonImageLoader.get(PlatformContext.INSTANCE) }

    // A desktop window that is running counts as foreground; there is no process lifecycle to observe.
    @Provides
    @Singleton
    fun appForeground(): AppForeground = object : AppForeground {
        override val isForeground: Boolean = true
        override fun observe(observer: androidx.lifecycle.DefaultLifecycleObserver) = Unit
    }

    // No system notifications on desktop yet, so there is nothing for chat to dismiss.
    @Provides
    @Singleton
    fun chatNotifications(): ChatNotifications = ChatNotifications.None

    // HostInvites keeps its @Inject on a secondary constructor, which Metro's Dagger interop does not read.
    @Provides
    @Singleton
    fun hostInvites(
        chatController: ChatController,
        groupKeys: GroupKeySource,
        keys: ChatKeySource,
        cipher: InviteCipher,
    ): HostInvites = HostInvites(chatController, groupKeys, keys, cipher)

    // RealChatCoordinator and MessagingDelegate take these as `T? = null`. Dagger sees them as required; Metro, with
    // REQUIRE_OPTIONAL_BINDING (see build.gradle.kts), wants a binding for the nullable type itself.
    @Provides
    fun optionalHostInvites(value: HostInvites): HostInvites? = value

    @Provides
    fun optionalPaymentRequests(value: PaymentRequests): PaymentRequests? = value

    @Provides
    fun optionalIncomingOpener(value: IncomingMessageOpener): IncomingMessageOpener? = value

    @Provides
    fun optionalGroupKeyEnvelopes(value: GroupKeyEnvelopeStore): GroupKeyEnvelopeStore? = value

    // emojis' ReactionsModule is a Dagger module that module does not run Dagger's compiler for on the JVM,
    // so Metro cannot read it; this is its provider.
    @Provides
    @Singleton
    fun recentReactionsStore(files: PreferencesFiles, owner: RecentReactionsOwner): RecentReactionsStore =
        DataStoreRecentReactionsStore(files, owner)

    @Provides
    @Singleton
    fun resources(): ResourceHelper = ComposeResourceHelper()

    @Provides
    fun tokenMetadata(coordinator: TokenCoordinator): TokenMetadataProvider = coordinator

    // Android adds TokenCoordinator to the listener set in TokenModule, which is Android-only. Without this
    // the coordinator never sees onUserLoggedIn and the wallet stays on its loading state.
    @Provides
    @IntoSet
    fun tokenSessionListener(coordinator: TokenCoordinator): SessionListener = coordinator

    @Provides
    @Singleton
    fun purchaseMethods(): PurchaseMethodController = object : PurchaseMethodController {
        override val state: StateFlow<PurchaseMethodState> = MutableStateFlow(PurchaseMethodState())
        override val selections: Flow<PurchaseMethodSelection> = emptyFlow()
        override fun present(metadata: PurchaseMethodMetadata) = Unit
        override fun select(method: PurchaseMethod, metadata: PurchaseMethodMetadata) = Unit
        override suspend fun presentDepositOptions(popToRoot: Boolean): AppRoute? = null
    }
}
