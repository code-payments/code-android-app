package com.getcode.libs.emojis

interface EmojiUsageTracker {
    suspend fun mostUsedEmojis(includeFiller: Boolean = true): List<String>
    suspend fun hasUsedAny(): Boolean
}

object StubEmojiTracker: EmojiUsageTracker {
    override suspend fun mostUsedEmojis(includeFiller: Boolean): List<String> = emptyList()
    override suspend fun hasUsedAny(): Boolean = false
}