package com.getcode.ui.emojis

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.libs.emojis.EmojiUsageTracker
import com.getcode.libs.emojis.StubEmojiTracker

val LocalEmojiUsageController = staticCompositionLocalOf<EmojiUsageTracker> { StubEmojiTracker }