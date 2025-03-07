package com.getcode.ui.emojis

import android.content.Context
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import java.util.concurrent.Executors

object EmojiCompatController {
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            // Use bundled config for simplicity (offline support)
            val config = BundledEmojiCompatConfig(context, Executors.newSingleThreadExecutor())

            // Alternative: Use downloadable fonts (requires Google Play Services)
            /*
            val config = FontRequestEmojiCompatConfig(
                context,
                FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    "Noto Color Emoji Compat",
                    R.array.com_google_android_gms_fonts_certs
                )
            )
            */

            EmojiCompat.init(config)
            isInitialized = true
        }
    }

    internal fun processEmoji(text: String): CharSequence {
        return EmojiCompat.get().process(text) ?: text
    }
}

fun processEmoji(text: String): CharSequence = EmojiCompatController.processEmoji(text)