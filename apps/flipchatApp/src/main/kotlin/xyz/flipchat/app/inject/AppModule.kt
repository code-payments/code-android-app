package xyz.flipchat.app.inject

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.getcode.libs.emojis.EmojiQueryProvider
import com.getcode.libs.emojis.EmojiUsageController
import com.getcode.libs.emojis.EmojiUsageTracker
import com.getcode.libs.opengraph.OpenGraphCacheProvider
import com.getcode.libs.opengraph.OpenGraphParser
import com.getcode.libs.opengraph.cache.CacheProvider
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.resources.AndroidResources
import com.getcode.util.resources.AndroidSettingsHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.SettingsHelper
import com.getcode.utils.CurrencyUtils
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.flipchat.app.BuildConfig
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.beta.LabsController
import xyz.flipchat.app.util.AndroidLocale
import xyz.flipchat.app.util.AndroidPermissions
import xyz.flipchat.app.util.FcTab
import xyz.flipchat.app.util.Router
import xyz.flipchat.app.util.RouterImpl
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.internal.EmojiQueryProviderImpl
import xyz.flipchat.services.analytics.FlipchatAnalyticsManager
import xyz.flipchat.services.analytics.FlipchatAnalyticsService
import xyz.flipchat.services.billing.BillingClient
import xyz.flipchat.services.billing.GooglePlayBillingClient
import xyz.flipchat.services.billing.StubBillingClient
import xyz.flipchat.services.internal.network.repository.iap.InAppPurchaseRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun providesResourceHelper(
        @ApplicationContext context: Context,
    ): ResourceHelper = AndroidResources(context)

    @Provides
    fun providesSettingsHelper(
        @ApplicationContext context: Context,
    ): SettingsHelper = AndroidSettingsHelper(context)

    @Provides
    fun providesLocaleHelper(
        @ApplicationContext context: Context,
        currencyUtils: CurrencyUtils,
    ): LocaleHelper = AndroidLocale(context, currencyUtils)

    @Provides
    fun providesWifiManager(
        @ApplicationContext context: Context,
    ): WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Provides
    fun providesConnectivityManager(
        @ApplicationContext context: Context,
    ): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    fun providesTelephonyManager(
        @ApplicationContext context: Context,
    ): TelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Provides
    fun providesPermissionChecker(
        @ApplicationContext context: Context,
    ): PermissionChecker = AndroidPermissions(context)

    @Provides
    fun providesClipboard(
        @ApplicationContext context: Context
    ): ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager


    @Provides
    fun providesNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManagerCompat = NotificationManagerCompat.from(context)

    // TODO:
    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI
    ): FlipchatAnalyticsService = FlipchatAnalyticsManager(mixpanelAPI)

    @Provides
    fun providesDeeplinkRouter(
        labs: Labs,
        userManager: UserManager,
        chatsController: ChatsController,
        resources: ResourceHelper,
    ): Router = RouterImpl(
        labs = labs,
        userManager = userManager,
        chatsController = chatsController,
        resources = resources,
        tabIndexResolver = { resolved ->
            when (resolved) {
                FcTab.Chat -> FcTab.Chat.ordinal
                FcTab.Cash -> FcTab.Cash.ordinal
                FcTab.Settings -> FcTab.Settings.ordinal
                FcTab.Profile -> FcTab.Profile.ordinal
            }
        },
        indexTabResolver = { index -> FcTab.entries[index] }
    )

    @Singleton
    @Provides
    fun providesBetaController(
        @ApplicationContext context: Context
    ): Labs = LabsController(context)

    @Singleton
    @Provides
    fun providesBillingController(
        @ApplicationContext context: Context,
        userManager: UserManager,
        purchaseRepository: InAppPurchaseRepository
    ): BillingClient = if (BuildConfig.DEBUG) {
        StubBillingClient
    } else {
        GooglePlayBillingClient(context, userManager, purchaseRepository)
    }

    @Singleton
    @Provides
    fun providesOpenGraphCache(
        @ApplicationContext context: Context,
    ): CacheProvider = OpenGraphCacheProvider(context)

    @Singleton
    @Provides
    fun providesOpenGraphParser(
        cache: CacheProvider
    ): OpenGraphParser = OpenGraphParser(cacheProvider = cache)

    @Singleton
    @Provides
    fun provideEmojiQueryProvider(
        userManager: UserManager,
    ): EmojiQueryProvider = EmojiQueryProviderImpl(userManager)

    @Singleton
    @Provides
    fun providesEmojiUsageController(
        queryProvider: EmojiQueryProvider
    ): EmojiUsageTracker = EmojiUsageController(queryProvider)
}