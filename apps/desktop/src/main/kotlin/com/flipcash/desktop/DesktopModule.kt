package com.flipcash.desktop

import com.flipcash.app.analytics.FlipcashAnalytics
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
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ComposeResourceHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkObserverStub
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

    // ChatCryptoModule provides this from its companion object, which Metro's Dagger interop
    // does not read from a precompiled module.
    @Provides
    fun chatCipher(): ChatCipher = DefaultChatCipher

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
