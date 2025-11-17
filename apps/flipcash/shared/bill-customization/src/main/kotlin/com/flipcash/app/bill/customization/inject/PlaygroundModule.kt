package com.flipcash.app.bill.customization.inject

import android.content.ClipboardManager
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
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
    ): BillPlaygroundController = InternalBillPlaygroundController(clipboardManager)
}