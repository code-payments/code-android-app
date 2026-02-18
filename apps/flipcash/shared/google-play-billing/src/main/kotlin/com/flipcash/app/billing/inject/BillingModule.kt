package com.flipcash.app.billing.inject

import android.content.Context
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.billing.internal.GooglePlayBillingClient
import com.flipcash.services.controllers.PurchaseController
import com.flipcash.services.user.UserManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    internal fun providesBillingClient(
        @ApplicationContext context: Context,
        purchases: PurchaseController,
        userManager: UserManager,
        analytics: FlipcashAnalyticsService
    ): BillingClient = GooglePlayBillingClient(context, userManager, purchases, analytics)
}