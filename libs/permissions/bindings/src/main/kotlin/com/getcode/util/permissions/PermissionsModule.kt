package com.getcode.util.permissions

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
object PermissionsModule {

    @Provides
    fun providesPermissionChecker(
        activity: Activity
    ): PermissionChecker = AndroidPermissionChecker(activity)
}