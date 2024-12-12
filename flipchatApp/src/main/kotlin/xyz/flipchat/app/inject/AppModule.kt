package xyz.flipchat.app.inject

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibratorManager
import android.support.v4.os.IResultReceiver.Stub
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.getcode.services.analytics.AnalyticsService
import com.getcode.services.analytics.AnalyticsServiceNull
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.AndroidResources
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Api25Vibrator
import com.getcode.util.vibration.Api26Vibrator
import com.getcode.util.vibration.Api31Vibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.network.Api24NetworkObserver
import com.getcode.utils.network.Api29NetworkObserver
import com.getcode.utils.network.NetworkConnectivityListener
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.flipchat.app.BuildConfig
import xyz.flipchat.app.beta.BetaFlagController
import xyz.flipchat.app.beta.BetaFlags
import xyz.flipchat.app.util.AndroidLocale
import xyz.flipchat.app.util.FcTab
import xyz.flipchat.app.util.Router
import xyz.flipchat.app.util.RouterImpl
import xyz.flipchat.services.billing.BillingController
import xyz.flipchat.services.billing.GooglePlayBillingController
import xyz.flipchat.services.billing.StubBillingController
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
    fun providesLocaleHelper(
        @ApplicationContext context: Context,
        currencyUtils: CurrencyUtils,
    ): LocaleHelper = AndroidLocale(context, currencyUtils)

    @Provides
    fun providesWifiManager(
        @ApplicationContext context: Context,
    ): WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Provides
    fun providesConnectivityManager(
        @ApplicationContext context: Context,
    ): ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    fun providesTelephonyManager(
        @ApplicationContext context: Context,
    ): TelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Provides
    fun providesClipboard(
        @ApplicationContext context: Context
    ): ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager


    @Provides
    fun providesNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManagerCompat = NotificationManagerCompat.from(context)

    @SuppressLint("NewApi")
    @Provides
    @Singleton
    fun providesVibrator(
        @ApplicationContext context: Context
    ): Vibrator = when (val apiLevel = Build.VERSION.SDK_INT) {
        in Build.VERSION_CODES.BASE..Build.VERSION_CODES.R -> {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (apiLevel >= Build.VERSION_CODES.O) {
                Api26Vibrator(vibrator)
            } else {
                Api25Vibrator(vibrator)
            }
        }
        else -> Api31Vibrator(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
    }

    @Provides
    @SuppressLint("NewApi")
    @Singleton
    fun providesNetworkObserver(
        connectivityManager: ConnectivityManager,
        telephonyManager: TelephonyManager,
        wifiManager: WifiManager
    ): NetworkConnectivityListener = when (Build.VERSION.SDK_INT) {
        in Build.VERSION_CODES.N .. Build.VERSION_CODES.P -> {
            Api24NetworkObserver(
                wifiManager,
                connectivityManager,
                telephonyManager
            )
        }
        else -> Api29NetworkObserver(
            connectivityManager,
            telephonyManager
        )
    }

    // TODO:
    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI
    ): AnalyticsService = AnalyticsServiceNull()

    @Provides
    fun providesDeeplinkRouter(
        userManager: UserManager
    ): Router = RouterImpl(
            userManager = userManager,
            tabIndexResolver = { resolved ->
                when (resolved) {
                    FcTab.Chat -> FcTab.Chat.ordinal
                    FcTab.Cash -> FcTab.Cash.ordinal
                    FcTab.Settings -> FcTab.Settings.ordinal
                }
            },
            indexTabResolver = { index -> FcTab.entries[index] }
        )

    @Singleton
    @Provides
    fun providesBetaController(
        @ApplicationContext context: Context
    ): BetaFlags = BetaFlagController(context)

    @Singleton
    @Provides
    fun providesBillingController(
        @ApplicationContext context: Context
    ): BillingController = if (BuildConfig.DEBUG) {
        StubBillingController
    } else {
        GooglePlayBillingController(context)
    }
}