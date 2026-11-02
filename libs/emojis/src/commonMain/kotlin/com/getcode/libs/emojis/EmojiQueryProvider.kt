package com.getcode.libs.emojis

interface EmojiQueryProvider {
    suspend fun getFrequentEmojis(): List<String>
}