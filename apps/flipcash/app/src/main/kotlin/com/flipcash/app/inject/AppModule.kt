package com.flipcash.app.inject

import android.content.ClipboardManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
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

//
//    @Singleton
//    @Provides
//    fun providesBetaController(
//        @ApplicationContext context: Context
//    ): Labs = LabsController(context)
}