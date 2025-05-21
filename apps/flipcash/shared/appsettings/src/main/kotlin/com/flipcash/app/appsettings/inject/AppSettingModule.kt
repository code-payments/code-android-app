package com.flipcash.app.appsettings.inject

import android.content.Context
import com.flipcash.app.appsettings.AppSettingsController
import com.flipcash.app.appsettings.internal.InternalAppSettingsController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppSettingModule {
    @Provides
    @Singleton
    fun providesAppSettingsController(
        @ApplicationContext context: Context,
    ): AppSettingsController = InternalAppSettingsController(context)
}