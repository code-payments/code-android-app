package com.flipcash.app.updates.internal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** The version the "New version available" toast last showed for. */
internal interface UpdateToastSeenStore {
    val lastShownVersionCode: Flow<Int?>
    suspend fun markShown(versionCode: Int)
}

private val Context.updateToastDataStore by preferencesDataStore(name = "update-toast")

private val LAST_SHOWN_KEY = intPreferencesKey("last_shown_version_code")

internal class DataStoreUpdateToastSeenStore(private val context: Context) : UpdateToastSeenStore {

    private val freshInstallCheck = Mutex()
    private var checkedFreshInstall = false

    /**
     * Clears what a restored backup brought along on a fresh install: a version seen on the old
     * device says nothing about this one. The marker lives in noBackupFilesDir, so it is missing
     * exactly when the install is new.
     */
    private suspend fun clearIfFreshInstall() = freshInstallCheck.withLock {
        if (checkedFreshInstall) return@withLock
        val marker = File(context.noBackupFilesDir, "update-toast-initialized")
        if (!marker.exists()) {
            context.updateToastDataStore.edit { it.clear() }
            marker.createNewFile()
        }
        checkedFreshInstall = true
    }

    override val lastShownVersionCode: Flow<Int?> = flow {
        clearIfFreshInstall()
        emitAll(context.updateToastDataStore.data.map { it[LAST_SHOWN_KEY] })
    }

    override suspend fun markShown(versionCode: Int) {
        clearIfFreshInstall()
        context.updateToastDataStore.edit { it[LAST_SHOWN_KEY] = versionCode }
    }
}
