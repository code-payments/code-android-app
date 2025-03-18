package com.getcode.libs.emojis

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmojiUsageController @Inject constructor(
    @ApplicationContext context: Context
): EmojiUsageTracker {
    private companion object {
        val DEFAULT_TOP = listOf("👍", "👎", "❤️", "🙌", "😂")
        private const val USAGE_COUNT_KEY_PREFIX = "emoji_usage_"
        private const val MAX_EMOJIS = 5
    }

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storage = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("emojis") }
    )

    override fun trackUsageOf(emoji: String) {
        dataScope.launch {
            storage.updateData { preferences ->
                val currentCount = preferences[stringPreferencesKey(USAGE_COUNT_KEY_PREFIX + emoji)]?.toIntOrNull() ?: 0
                preferences.toMutablePreferences().apply {
                    this[stringPreferencesKey(USAGE_COUNT_KEY_PREFIX + emoji)] = (currentCount + 1).toString()
                }
            }
        }
    }

    override suspend fun mostUsedEmojis(includeFiller: Boolean): List<String> {
        return withContext(Dispatchers.IO) {
            val preferences = storage.data.firstOrNull() ?: emptyPreferences()
            val emojiUsageMap = mutableMapOf<String, Int>()

            // Collect all emoji usage counts from preferences
            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith(USAGE_COUNT_KEY_PREFIX)) {
                    val emoji = key.name.removePrefix(USAGE_COUNT_KEY_PREFIX)
                    val count = value.toString().toIntOrNull() ?: 0
                    emojiUsageMap[emoji] = count
                }
            }

            // Get top emojis based on usage
            val topEmojis = emojiUsageMap.entries
                .sortedByDescending { it.value }  // Sort by usage count
                .map { it.key }                   // Get just the emojis

            if (!includeFiller) {
                return@withContext topEmojis
            }

            // Ensure exactly 5 emojis
            when {
                topEmojis.size > MAX_EMOJIS -> topEmojis.take(MAX_EMOJIS)
                topEmojis.size < MAX_EMOJIS -> {
                    val remainingCount = MAX_EMOJIS - topEmojis.size
                    (topEmojis + DEFAULT_TOP.filter { it !in topEmojis }.take(remainingCount))
                }
                else -> topEmojis
            }
        }
    }

    override suspend fun hasUsedAny(): Boolean {
        return storage.data.toList().isNotEmpty()
    }
}