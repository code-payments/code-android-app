package com.flipcash.app.currencycreator.internal

/**
 * The currency name rule, from `CheckAvailabilityRequest.name` and `LaunchRequest.name` in the
 * pinned OCP contract: `min_len 1`, `max_len 32` and `pattern ^[!-~]([ -~]*[!-~])?$`, which is
 * printable ASCII with no leading or trailing space.
 *
 * Checked on the trimmed text, since trimmed text is what both requests send. Every allowed
 * character is ASCII, so [String.length] counts it the way the server does.
 */
internal object CurrencyName {
    const val MAX_LENGTH = 32

    private val pattern = Regex("^[!-~]([ -~]*[!-~])?$")

    fun normalize(raw: CharSequence): String = raw.toString().trim()

    fun isValid(raw: CharSequence): Boolean {
        val name = normalize(raw)
        return name.length in 1..MAX_LENGTH && pattern.matches(name)
    }
}
