package com.getcode.libs.emojis.reactions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

private val Context.recentReactionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_reactions")

/** Per-emoji use counts and last-used times, backing [RecentReactions.rank]. */
interface RecentReactionsStore {

    /** Records one use of [emoji] now. Only an add records — a remove does not. */
    suspend fun record(emoji: String)

    /** Returns up to [limit] emoji in rank order, leaving out [undrawable]. */
    suspend fun rank(undrawable: Set<String> = emptySet(), limit: Int = RecentReactions.DEFAULTS.size): List<String>

    /** The raw per-emoji use stats, for a caller (e.g. [com.flipcash.shared.chat.reactions.ReactionStripComposer]) that ranks them itself. */
    suspend fun stats(): Map<String, RecentReactions.Usage>
}

@Singleton
class DataStoreRecentReactionsStore @Inject constructor(
    @ApplicationContext context: Context,
) : RecentReactionsStore {

    private val dataStore = context.recentReactionsDataStore

    override suspend fun record(emoji: String) {
        val now = Clock.System.now()
        dataStore.edit { prefs ->
            val key = key(emoji)
            val current = decode(prefs[key])
            prefs[key] = encode(RecentReactions.Usage(count = current.count + 1, lastUsed = now))
        }
    }

    override suspend fun rank(undrawable: Set<String>, limit: Int): List<String> =
        RecentReactions.rank(stats(), undrawable, limit)

    override suspend fun stats(): Map<String, RecentReactions.Usage> {
        val prefs = dataStore.data.first()
        return prefs.asMap().mapNotNull { (prefsKey, raw) ->
            val emoji = prefsKey.name.removePrefix(KEY_PREFIX)
            if (emoji == prefsKey.name) return@mapNotNull null // not one of ours
            val usage = decode(raw as? String) ?: return@mapNotNull null
            emoji to usage
        }.toMap()
    }

    private fun key(emoji: String) = stringPreferencesKey("$KEY_PREFIX$emoji")

    private fun encode(usage: RecentReactions.Usage): String = "${usage.count}:${usage.lastUsed.epochSeconds}"

    private fun decode(raw: String?): RecentReactions.Usage {
        if (raw == null) return RecentReactions.Usage(count = 0, lastUsed = Instant.fromEpochSeconds(0))
        val separator = raw.indexOf(':')
        if (separator < 0) return RecentReactions.Usage(count = 0, lastUsed = Instant.fromEpochSeconds(0))
        val count = raw.substring(0, separator).toIntOrNull() ?: 0
        val lastUsed = raw.substring(separator + 1).toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
            ?: Instant.fromEpochSeconds(0)
        return RecentReactions.Usage(count = count, lastUsed = lastUsed)
    }

    private companion object {
        const val KEY_PREFIX = "emoji:"
    }
}
