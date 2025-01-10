package xyz.flipchat.app.beta

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Lab {
    val key: String
    val default: Boolean
    val launched: Boolean

    data object ReplyToMessage : Lab {
        override val key = "pref_reply_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
    }

    data object FollowerMode : Lab {
        override val key: String = "pref_follower_mode_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
    }

    data object StartChatAtUnread : Lab {
        override val key: String = "pref_start_at_unread_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
    }

    data object RoomNameChanges : Lab {
        override val key: String = "pref_room_name_changes_enabled"
        override val default: Boolean = true
        override val launched: Boolean = true
    }

    data object DeleteMessage : Lab {
        override val key: String = "delete_message_enabled"
        override val default: Boolean = false
        override val launched: Boolean = false
    }

    companion object {
        val entries = listOf(ReplyToMessage, FollowerMode, StartChatAtUnread, RoomNameChanges, DeleteMessage)
        internal fun byKey(key: Preferences.Key<*>): Lab? {
            return entries.firstOrNull { it.key == key.name }
        }
    }
}

data class BetaFeature(
    val flag: Lab,
    val enabled: Boolean,
)

private val Lab.preferenceKey
    get() = booleanPreferencesKey(key)

class LabsController @Inject constructor(
    @ApplicationContext context: Context,
) : Labs {
    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val betaFlags = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("beta-flags") }
    )

    init {
        // reset launched flags
        Lab.entries
            .filter { it.launched }
            .onEach { reset(it) }
    }

    override fun set(flag: Lab, value: Boolean) {
        dataScope.launch(Dispatchers.IO) {
            betaFlags.edit { prefs ->
                prefs[flag.preferenceKey] = value
            }
        }
    }

    override suspend fun get(flag: Lab): Boolean {
        return betaFlags.data.map { prefs ->
            if (flag.launched) return@map flag.default
            prefs[flag.preferenceKey] ?: flag.default
        }.firstOrNull() ?: flag.default
    }

    override fun observe(flag: Lab): StateFlow<Boolean> = betaFlags.data.map { prefs ->
        if (flag.launched) return@map flag.default
        prefs[flag.preferenceKey] ?: flag.default
    }.stateIn(dataScope, started = SharingStarted.Eagerly, flag.default)

    override fun observe(): StateFlow<List<BetaFeature>> = betaFlags.data.map { prefs ->
        Lab.entries.filterNot { it.launched }.map {
            val value = if (it.launched) {
                it.default
            } else {
                prefs[it.preferenceKey] ?: it.default
            }

            BetaFeature(it, value)
        }
    }.stateIn(
        dataScope,
        started = SharingStarted.Eagerly,
        Lab.entries.map { BetaFeature(it, it.default) }
    )

    override fun reset(flag: Lab) {
        dataScope.launch {
            betaFlags.edit { it.remove(flag.preferenceKey) }
        }
    }

    override fun reset() {
        dataScope.launch {
            betaFlags.edit { it.clear() }
        }
    }
}