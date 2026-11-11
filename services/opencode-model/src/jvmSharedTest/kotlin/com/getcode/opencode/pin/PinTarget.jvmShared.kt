package com.getcode.opencode.pin

import java.util.Locale

/** The host JDK (desktop JVM and Android host tests), whose differences from Android are pinned in host-jdk.txt. */
internal actual fun pinTarget(): PinTarget {
    val tags = listOf("en-US", "de-DE", "en-IN")
    val previous = Locale.getDefault()
    val lines = try {
        tags.flatMap { tag ->
            Locale.setDefault(Locale.forLanguageTag(tag))
            LocaleFormattingCases.cases().map { (name, out) -> "locale|$tag|$name|$out" }
        }
    } finally {
        Locale.setDefault(previous)
    }
    return PinTarget(fixture = "host-jdk.txt", localeLines = lines, localeTags = tags.toSet())
}
