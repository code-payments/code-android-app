package com.flipcash.app.persistence.sources.search

import org.junit.Test
import kotlin.test.assertEquals

class MemberSearchTextTest {

    @Test
    fun `normalizing folds case and diacritics`() {
        assertEquals("erica", MemberSearchText.normalize("Érica"))
        assertEquals("zoe", MemberSearchText.normalize("ZOË"))
        assertEquals("francois", MemberSearchText.normalize("François"))
    }

    @Test
    fun `normalizing folds compatibility forms`() {
        // Full-width letters and the "ﬁ" ligature, via NFKD.
        assertEquals("abc", MemberSearchText.normalize("ＡＢＣ"))
        assertEquals("fish", MemberSearchText.normalize("ﬁsh"))
    }

    @Test
    fun `a dotted capital I folds to a plain i`() {
        assertEquals("istanbul", MemberSearchText.normalize("İstanbul"))
    }

    @Test
    fun `tokens are each display name word plus the bare handle`() {
        assertEquals(
            setOf("maria", "jose", "garcia", "mjg"),
            MemberSearchText.tokens("María José  García", "@mjg"),
        )
    }

    @Test
    fun `a member with no name is found by handle alone`() {
        assertEquals(setOf("mjg"), MemberSearchText.tokens(null, "mjg"))
        assertEquals(emptySet(), MemberSearchText.tokens("", null))
    }

    @Test
    fun `a query drops the mention trigger and folds like the tokens`() {
        assertEquals(listOf("eri"), MemberSearchText.queryWords("@Éri"))
        assertEquals(listOf("mar", "ga"), MemberSearchText.queryWords(" Mar  Ga "))
        assertEquals(emptyList(), MemberSearchText.queryWords("@"))
    }

    @Test
    fun `a query word loses exactly one leading mention trigger`() {
        // The second @ is part of the word, so "@@eri" does not match a token "eri".
        assertEquals(listOf("@eri"), MemberSearchText.queryWords("@@eri"))
        // A full-width ＠ folds to @ under NFKD and is stripped the same way.
        assertEquals(listOf("eri"), MemberSearchText.queryWords("＠eri"))
        assertEquals(listOf("a", "b"), MemberSearchText.queryWords("@a @b"))
    }

    @Test
    fun `the upper bound sorts above any continuation of a prefix`() {
        // SQLite compares TEXT as UTF-8 bytes; so does this.
        fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        val bound = bytes("er" + MemberSearchText.UPPER_BOUND)
        for (token in listOf("er", "erica", "er￿", "er😀")) {
            val t = bytes(token)
            val cmp = t.zip(bound).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a - b } ?: (t.size - bound.size)
            assert(cmp < 0) { "$token should sort below the bound" }
        }
    }
}
