package com.flipcash.app.core.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

/** [PreferencesFiles] backed by [preferencesDataStoreFile], the location DataStore has always used. */
internal class ContextPreferencesFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreferencesFiles {
    override fun file(name: String): File = context.preferencesDataStoreFile(name)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PreferencesFilesModule {
    @Binds
    abstract fun bindPreferencesFiles(impl: ContextPreferencesFiles): PreferencesFiles
}
