package com.flipcash.shared.chat.ui

import android.util.Patterns

internal actual fun findWebUrls(text: String): List<IntRange> = buildList {
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        // An empty match cannot happen for this pattern, but an empty range would underflow below.
        if (matcher.end() > matcher.start()) add(matcher.start() until matcher.end())
    }
}
