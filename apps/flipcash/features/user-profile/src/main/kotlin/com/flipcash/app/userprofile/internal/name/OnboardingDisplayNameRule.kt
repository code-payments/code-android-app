package com.flipcash.app.userprofile.internal.name

internal const val ONBOARDING_DISPLAY_NAME_MAX_LENGTH = 64

/**
 * The onboarding display-name rule, shared in definition with iOS: after trimming leading and
 * trailing whitespace the name is non-empty, at most [ONBOARDING_DISPLAY_NAME_MAX_LENGTH]
 * characters, and made only of ASCII letters, ASCII digits and the space character (U+0020).
 * Any other whitespace (tab, newline, NBSP), punctuation, accented letters or emoji is invalid.
 * Runs of internal spaces are allowed.
 *
 * Pure: no Android types, so it can move to shared code.
 */
internal fun isValidOnboardingDisplayName(raw: String): Boolean {
    val name = trimOnboardingDisplayName(raw)
    return name.isNotEmpty() &&
        name.length <= ONBOARDING_DISPLAY_NAME_MAX_LENGTH &&
        name.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' }
}

/** The string onboarding submits: [raw] trimmed with [isOnboardingTrimmable] on both ends. */
internal fun trimOnboardingDisplayName(raw: String): String = raw.trim(::isOnboardingTrimmable)

// Mirrors iOS CharacterSet.whitespacesAndNewlines: U+0009-U+000D, U+0085, and categories Zs/Zl/Zp.
// Not Char.isWhitespace(), which also strips U+001C-U+001F and misses U+0085.
private fun isOnboardingTrimmable(c: Char): Boolean =
    c.code in 0x09..0x0D || c.code == 0x85 ||
        when (Character.getType(c).toByte()) {
            Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
            else -> false
        }
