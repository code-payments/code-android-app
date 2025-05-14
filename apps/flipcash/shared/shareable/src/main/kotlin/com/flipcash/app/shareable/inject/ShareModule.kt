package com.flipcash.app.shareable.inject

import android.content.ClipboardManager
import android.content.Context
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.internal.InternalShareSheetController
import com.getcode.opencode.controllers.BalanceController
import com.getcode.util.resources.ResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShareModule {

    @Provides
    @Singleton
    fun provideShareSheetController(
        @ApplicationContext
        context: Context,
        clipboardManager: ClipboardManager,
        resources: ResourceHelper,
        balanceController: BalanceController,
    ): ShareSheetController = InternalShareSheetController(
        context = context,
        clipboardManager = clipboardManager,
        resources = resources,
        balanceController = balanceController
    )
}