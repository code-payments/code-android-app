package com.flipcash.app.inject

import android.content.ClipboardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import com.flipcash.android.app.BuildConfig
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.annotations.AccountType
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providesAppVersion(): VersionInfo = VersionInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE
    )

    @Provides
    @AccountType
    fun providesAccountType(): String = "flipcash.com"

    @Provides
    fun providesResourceHelper(
        @ApplicationContext context: Context,
    ): ResourceHelper = AndroidResources(context)

    @Provides
    fun providesSettingsHelper(
        @ApplicationContext context: Context,
    ): SettingsHelper = AndroidSettingsHelper(context)

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

    @Provides
    @Singleton
    fun providesBiometricsManager(
        @ApplicationContext context: Context
    ): BiometricManager = BiometricManager.from(context)
}