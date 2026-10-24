package com.flipcash.app.userflags

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

internal class AndroidUserFlagsFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UserFlagsFiles {
    override val noBackupDir: File get() = context.noBackupFilesDir
    override fun dataStoreFile(name: String): File = context.preferencesDataStoreFile(name)
}
