package com.flipcash.app.updates.inject

import android.content.Context
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.internal.GooglePlayAppUpdateController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)  // ← Activity-scoped!
object AppUpdateModule {

    @Provides
    @ActivityScoped
    fun provideInAppUpdateManager(
        @ActivityContext context: Context,
        versionInfo: VersionInfo,
        userFlagsCoordinator: UserFlagsCoordinator,
    ): AppUpdateController = GooglePlayAppUpdateController(context, versionInfo, userFlagsCoordinator)
}