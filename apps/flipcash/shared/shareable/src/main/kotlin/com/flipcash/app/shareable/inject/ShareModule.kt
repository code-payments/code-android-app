package com.flipcash.app.shareable.inject

import android.content.ClipboardManager
import android.content.Context
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.ShareableConfirmationController
import com.flipcash.app.shareable.internal.InternalShareConfirmationController
import com.flipcash.app.shareable.internal.InternalShareSheetController
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
    ): ShareSheetController = InternalShareSheetController(
        context = context,
        clipboardManager = clipboardManager,
        resources = resources,
    )

    @Provides
    @Singleton
    fun provideShareConfirmationController(
        billController: BillController,
        resources: ResourceHelper,
    ): ShareableConfirmationController = InternalShareConfirmationController(
        billController = billController,
        resources = resources
    )
}