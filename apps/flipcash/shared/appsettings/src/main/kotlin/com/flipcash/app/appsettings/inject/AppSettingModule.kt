package com.flipcash.app.appsettings.inject

import android.content.Context
import com.flipcash.app.appsettings.AppSettingsController
import com.flipcash.app.appsettings.internal.InternalAppSettingsController
import com.flipcash.app.appsettings.internal.InternalTrustedWebsites
import com.flipcash.app.core.links.TrustedWebsites
import com.flipcash.libs.coroutines.DispatcherProvider
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppSettingModule {
    @Provides
    @Singleton
    fun providesAppSettingsController(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): AppSettingsController = InternalAppSettingsController(context, dispatchers)

    @Provides
    @Singleton
    fun providesTrustedWebsites(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): TrustedWebsites = InternalTrustedWebsites(
        scope = CoroutineScope(SupervisorJob() + dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile(InternalTrustedWebsites.FILE_NAME) },
    )
}