package com.flipcash.app.core.util

object MessagingPackages {
    val whatsApp = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    val all = setOf(
        "org.thoughtcrime.securesms",       // Signal
        "org.telegram.messenger",           // Telegram
        "com.facebook.orca",                // Messenger
    ) + whatsApp
}
