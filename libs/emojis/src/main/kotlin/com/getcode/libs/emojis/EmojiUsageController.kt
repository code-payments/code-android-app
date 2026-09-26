package com.getcode.libs.emojis

import com.getcode.libs.emojis.reactions.RecentReactions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmojiUsageController(
    private val queryProvider: EmojiQueryProvider
): EmojiUsageTracker {
    private companion object {
        // The reaction strip's six defaults, so the picker's "frequently used" filler and the
        // strip agree on what an emoji-less history falls back to.
        val DEFAULT_TOP = RecentReactions.DEFAULTS
        private val MAX_EMOJIS = RecentReactions.DEFAULTS.size
    }

    override suspend fun mostUsedEmojis(includeFiller: Boolean): List<String> {
        return withContext(Dispatchers.IO) {
            // Get top emojis based on usage
            val topEmojis = queryProvider.getFrequentEmojis()

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
        return queryProvider.getFrequentEmojis().isNotEmpty()
    }
}