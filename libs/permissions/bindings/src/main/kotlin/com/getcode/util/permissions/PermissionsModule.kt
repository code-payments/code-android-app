package com.getcode.util.permissions

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PermissionsModule {

    @Provides
    @Singleton
    fun providesPermissionChecker(
        @ApplicationContext context: Context
    ): PermissionChecker = AndroidPermissions(context)
}