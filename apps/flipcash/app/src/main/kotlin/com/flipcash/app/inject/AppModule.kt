package com.flipcash.app.inject

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.flipcash.app.BuildConfig
import com.flipcash.app.core.AccountType
import com.flipcash.services.analytics.FlipcashAnalyticsManager
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.getcode.util.resources.AndroidResources
import com.getcode.util.resources.AndroidSettingsHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.SettingsHelper
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @AccountType
    fun providesAccountType(): String = BuildConfig.APPLICATION_ID

    @Provides
    fun providesResourceHelper(
        @ApplicationContext context: Context,
    ): ResourceHelper = AndroidResources(context)

    @Provides
    fun providesSettingsHelper(
        @ApplicationContext context: Context,
    ): SettingsHelper = AndroidSettingsHelper(context)

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
    fun providesClipboard(
        @ApplicationContext context: Context
    ): ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager


    @Provides
    fun providesNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManagerCompat = NotificationManagerCompat.from(context)

    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI
    ): FlipcashAnalyticsService = FlipcashAnalyticsManager(mixpanelAPI)

//
//    @Singleton
//    @Provides
//    fun providesBetaController(
//        @ApplicationContext context: Context
//    ): Labs = LabsController(context)

//    @Singleton
//    @Provides
//    fun providesBillingController(
//        @ApplicationContext context: Context,
//        userManager: UserManager,
//        purchaseRepository: InAppPurchaseRepository
//    ): BillingClient = if (BuildConfig.DEBUG) {
//        StubBillingClient
//    } else {
//        GooglePlayBillingClient(context, userManager, purchaseRepository)
//    }
//
}