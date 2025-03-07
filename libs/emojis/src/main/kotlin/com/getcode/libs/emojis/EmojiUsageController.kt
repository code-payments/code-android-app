package com.getcode.libs.emojis

import javax.inject.Inject

class EmojiUsageController @Inject constructor() {

    private companion object {
        val DEFAULT_TOP = listOf("➕", "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83C\uDF89", "\uD83D\uDE4C", "\uD83D\uDD25")
    }

    fun trackUsageOf(emoji: String) {

    }

    suspend fun mostUsedEmojis(): List<String> {
        return DEFAULT_TOP
    }
}