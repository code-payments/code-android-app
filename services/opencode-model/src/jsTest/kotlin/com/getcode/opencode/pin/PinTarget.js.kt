package com.getcode.opencode.pin

/** Node is held to the Android output. Its default locale can't be changed, so only en-US runs. */
internal actual fun pinTarget(): PinTarget {
    val locale = js("Intl.NumberFormat().resolvedOptions().locale") as String
    check(locale == "en-US") { "the locale pins need Node's default locale to be en-US (LANG=en_US.UTF-8 or C), not $locale" }
    val lines = LocaleFormattingCases.cases().map { (name, out) -> "locale|en-US|$name|$out" }
    return PinTarget(fixture = "android-api36.txt", localeLines = lines, localeTags = setOf("en-US"))
}
