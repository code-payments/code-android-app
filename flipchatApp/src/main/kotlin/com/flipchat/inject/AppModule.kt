package com.flipchat.inject

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.flipchat.util.AndroidLocale
import com.flipchat.util.AndroidResources
import com.getcode.analytics.AnalyticsManager
import com.getcode.analytics.AnalyticsService
import com.getcode.analytics.AnalyticsServiceNull
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
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
}