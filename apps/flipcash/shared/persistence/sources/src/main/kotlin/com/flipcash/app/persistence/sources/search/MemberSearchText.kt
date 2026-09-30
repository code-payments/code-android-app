package com.flipcash.app.persistence.sources.search

import com.flipcash.app.persistence.FlipcashDatabase
import java.text.Normalizer
import java.util.Locale

/**
 * How member names and handles become search tokens, and how a query is folded to meet them.
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
object MemberSearchText {

    /**
     * Appended to a prefix to bound its range from above. The highest code point, so every token
     * that starts with the prefix sorts below `prefix + UPPER_BOUND` under SQLite's byte-wise
     * comparison, including tokens whose next character lies outside the Basic Multilingual Plane
     * (an emoji), which a U+FFFF bound would leave out.
     */
    const val UPPER_BOUND: String = "􏿿"

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

/**
 * Re-derives [userIdHex]'s search tokens from their stored profile, in every chat they are in.
 *
 * For a profile write that does not go through a roster write, such as a sender resolved on its
 * own. Read back from the row rather than taken from what was written, because a partial write
 * keeps the fields it was not given.
 */
internal suspend fun FlipcashDatabase.reindexMemberProfile(userIdHex: String) {
    val profile = userProfileDao().getByUserId(userIdHex) ?: return
    chatMemberSearchDao().replaceTokensEverywhere(
        userIdHex,
        MemberSearchText.tokens(profile.displayName, profile.username),
    )
}
