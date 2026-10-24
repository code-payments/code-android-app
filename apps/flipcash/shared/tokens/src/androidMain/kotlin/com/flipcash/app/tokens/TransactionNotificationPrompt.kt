package com.flipcash.app.tokens

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces the "notify me when complete" ask that used to live on the Processing screen. Fast
 * completions skip that screen, so after one succeeds the app asks once, ever, whether the user
 * wants to hear about transactions that finish while they are away.
 *
 * A flow requests the prompt from its exit via [request]. That sets [pending] rather than emitting
 * an event, so the request survives until the app-level host is there to take it. The host
 * [consume]s it, checks the live permission state (which needs a composition) and calls
 * [markShown] when it presents.
 */
@Singleton
class TransactionNotificationPrompt internal constructor(
    private val store: ShownStore,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(DataStoreShownStore(context))

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Asks the host to consider showing the explainer. */
    fun request() {
        _pending.value = true
    }

    /** Clears [pending]; the host calls this when it takes the request. */
    fun consume() {
        _pending.value = false
    }

    suspend fun hasBeenShown(): Boolean = store.shown()

    /** Persists that the explainer was presented. "Not Now" counts; it is never shown again. */
    suspend fun markShown() = store.markShown()

    internal interface ShownStore {
        suspend fun shown(): Boolean
        suspend fun markShown()
    }
}

/** Provided at the app level so flow screens can request the prompt on exit. */
val LocalTransactionNotificationPrompt = staticCompositionLocalOf<TransactionNotificationPrompt> {
    error("No TransactionNotificationPrompt provided")
}

private val Context.notificationPromptDataStore by preferencesDataStore(name = "transaction-notification-prompt")

private val SHOWN_KEY = booleanPreferencesKey("explainer_shown")

internal class DataStoreShownStore(
    private val dataStore: DataStore<Preferences>,
    private val freshInstallMarker: File,
) : TransactionNotificationPrompt.ShownStore {

    constructor(context: Context) : this(
        dataStore = context.notificationPromptDataStore,
        freshInstallMarker = File(context.noBackupFilesDir, "transaction-notification-prompt-initialized"),
    )

    private val freshInstallCheck = Mutex()
    private var checkedFreshInstall = false

    /**
     * Clears what a restored backup brought along on a fresh install: "shown" on the old device
     * says nothing about this one. The marker lives in noBackupFilesDir, so it is missing exactly
     * when the install is new.
     */
    private suspend fun clearIfFreshInstall() = freshInstallCheck.withLock {
        if (checkedFreshInstall) return@withLock
        if (!freshInstallMarker.exists()) {
            dataStore.edit { it.clear() }
            freshInstallMarker.createNewFile()
        }
        checkedFreshInstall = true
    }

    override suspend fun shown(): Boolean {
        clearIfFreshInstall()
        return dataStore.data.map { it[SHOWN_KEY] ?: false }.first()
    }

    override suspend fun markShown() {
        clearIfFreshInstall()
        dataStore.edit { it[SHOWN_KEY] = true }
    }
}
