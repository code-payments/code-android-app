package com.getcode.libs.emojis

interface EmojiUsageTracker {
    fun trackUsageOf(emoji: String)
    suspend fun mostUsedEmojis(): List<String>
}

object StubEmojiTracker: EmojiUsageTracker {
    override fun trackUsageOf(emoji: String) = Unit
    override suspend fun mostUsedEmojis(): List<String> = emptyList()
}