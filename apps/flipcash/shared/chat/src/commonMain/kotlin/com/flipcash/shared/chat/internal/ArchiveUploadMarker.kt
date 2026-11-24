package com.flipcash.shared.chat.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.flipcash.app.core.storage.PreferencesFiles
import com.flipcash.services.user.UserManager
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this account has uploaded the archive the device kept before the server stored one.
 * Set once a flush leaves no write waiting.
 */
internal interface ArchiveUploadMarker {
    suspend fun isSet(): Boolean
    suspend fun set()
}

/** One flag per account, as [DataStoreApproveNewMembersStore] keeps its switches. */
@Singleton
internal class DataStoreArchiveUploadMarker(
    private val dataStore: DataStore<Preferences>,
    /** The signed-in account as hex, null when signed out. */
    private val currentOwner: () -> String?,
) : ArchiveUploadMarker {

    @Inject
    constructor(preferencesFiles: PreferencesFiles, userManager: UserManager) : this(
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { preferencesFiles.file(DATASTORE_NAME) },
        ),
        currentOwner = { userManager.accountId?.hexEncodedString() },
    )

    // An unreadable store reads as not uploaded, which costs at most a repeat of calls the
    // server answers the same way twice.
    override suspend fun isSet(): Boolean {
        val owner = currentOwner() ?: return false
        val prefs = dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return prefs[key(owner)] == true
    }

    override suspend fun set() {
        val owner = currentOwner() ?: return
        dataStore.edit { it[key(owner)] = true }
    }

    private fun key(owner: String) = booleanPreferencesKey("uploaded-$owner")

    private companion object {
        const val DATASTORE_NAME = "chat_archive_upload"
    }
}
