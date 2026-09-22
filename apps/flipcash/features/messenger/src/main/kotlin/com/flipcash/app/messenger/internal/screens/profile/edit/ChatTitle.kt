package com.flipcash.app.messenger.internal.screens.profile.edit

/**
 * `EditChatRequest.Title.value` — `min_len 1, max_len 64`, checked here before the call.
 *
 * The bound is on the *trimmed* title, and the trimmed title is what gets sent: surrounding
 * whitespace is invisible in every place a group title is drawn, so counting it would let a title
 * that looks empty through and a title that looks short fail.
 *
 * Length is counted in code points, not [String.length]. protovalidate's `max_len` on a string
 * counts Unicode code points, while Kotlin counts UTF-16 units, so a title of 40 emoji measures 80
 * here and 40 at the server. Using [String.length] would refuse titles the contract accepts, and
 * would do it only for non-BMP characters — the case least likely to be noticed.
 */
internal object ChatTitle {
    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 64

    /** The value to send for [input] — trimmed, as [isValid] measures it. */
    fun normalize(input: CharSequence): String = input.toString().trim()

    fun lengthOf(input: CharSequence): Int =
        normalize(input).let { it.codePointCount(0, it.length) }

    fun isValid(input: CharSequence): Boolean = lengthOf(input) in MIN_LENGTH..MAX_LENGTH
}
