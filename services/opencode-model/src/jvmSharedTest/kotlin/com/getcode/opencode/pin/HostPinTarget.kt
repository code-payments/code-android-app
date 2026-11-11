package com.getcode.opencode.pin

import java.util.Locale

/** Runs the locale cases under each tag on the host JDK and checks them against [fixture]. */
internal fun hostPinTarget(fixture: String): PinTarget {
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
    return PinTarget(fixture = fixture, localeLines = lines, localeTags = tags.toSet())
}
