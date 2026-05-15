package com.flipcash.app.bill.customization.inject

import android.content.ClipboardManager
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
import com.flipcash.app.featureflags.FeatureFlagController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaygroundModule {

    @Provides
    @Singleton
    fun providesPlaygroundController(
        clipboardManager: ClipboardManager,
        featureFlags: FeatureFlagController,
    ): BillPlaygroundController =
        InternalBillPlaygroundController(
            clipboard = clipboardManager,
            featureFlags = featureFlags,
        )
}