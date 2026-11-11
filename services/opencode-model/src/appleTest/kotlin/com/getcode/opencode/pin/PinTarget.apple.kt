package com.getcode.opencode.pin

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/** Apple is held to the Android output. Its default locale can't be changed at runtime, so only en-US runs. */
internal actual fun pinTarget(): PinTarget {
    val locale = NSLocale.currentLocale.localeIdentifier
    check(locale == "en_US" || locale.startsWith("en_US@")) { "the locale pins need the default locale to be en_US, not $locale" }
    val lines = LocaleFormattingCases.cases().map { (name, out) -> "locale|en-US|$name|$out" }
    return PinTarget(fixture = "android-api36.txt", localeLines = lines, localeTags = setOf("en-US"))
}
