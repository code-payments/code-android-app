package com.flipcash.app.invite.inject

import android.content.Context
import com.flipcash.app.invite.InviteController
import com.flipcash.app.invite.internal.InternalInviteController
import com.flipcash.app.shareable.ShareSheetController
import com.getcode.util.resources.ResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InviteModule {

    @Provides
    @Singleton
    fun provideInviteController(
        @ApplicationContext context: Context,
        resources: ResourceHelper,
        shareController: ShareSheetController,
    ): InviteController = InternalInviteController(
        context = context,
        resources = resources,
        shareController = shareController,
    )
}
