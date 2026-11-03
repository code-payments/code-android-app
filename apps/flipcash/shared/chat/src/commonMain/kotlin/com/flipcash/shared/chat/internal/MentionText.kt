package com.flipcash.shared.chat.internal

import java.text.Normalizer
import java.util.Locale

/**
 * How member names and handles are matched against what the user typed after `@`.
 *
 * Both sides go through [normalize], so matching is case- and diacritic-insensitive: "eri" finds
 * "Érica". iOS folds the same way, in the same order, so a query matches the same members on both
 * platforms:
 *
 * 1. Unicode NFKD, which splits "É" into "E" plus a combining acute and folds compatibility forms
 *    such as full-width letters and ligatures.
 * 2. Lowercase, locale-independent. After NFKD, not before: "İ" lowercases to "i" plus a combining
 *    dot, which step 3 then removes.
 * 3. Drop every nonspacing combining mark (general category Mn).
 */
internal object MentionText {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun normalize(text: String): String =
        combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase(Locale.ROOT), "")

    /** [text]'s normalized words, split on whitespace. */
    fun words(text: String): List<String> =
        normalize(text).split(whitespace).filter { it.isNotEmpty() }

    /** The tokens a member is found by: each word of [displayName], and [username] without its `@`. */
    fun tokens(displayName: String?, username: String?): Set<String> = buildSet {
        displayName?.let { addAll(words(it)) }
        username?.removePrefix("@")?.let { addAll(words(it)) }
    }

    /** [query]'s words, each a prefix to match. A leading `@` on a word is the mention trigger, not part of it. */
    fun queryWords(query: String): List<String> =
        words(query).map { it.removePrefix("@") }.filter { it.isNotEmpty() }
}
