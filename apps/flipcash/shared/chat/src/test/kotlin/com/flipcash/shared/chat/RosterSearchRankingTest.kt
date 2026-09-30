package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.RosterSearchCandidate
import com.flipcash.app.persistence.sources.search.MemberSearchText
import com.flipcash.shared.chat.internal.CodePointOrder
import com.flipcash.shared.chat.internal.rankRosterMatches
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RosterSearchRankingTest {

    private fun candidate(id: Int, name: String, username: String? = null, spokeAt: Long? = null) =
        RosterSearchCandidate(
            userId = listOf(id.toByte()),
            displayName = name,
            username = username,
            profilePicture = null,
            lastSpokeEpochMs = spokeAt,
        )

    private fun rank(query: String, vararg candidates: RosterSearchCandidate) =
        rankRosterMatches(candidates.toList(), MemberSearchText.queryWords(query)).map { it.displayName }

    @Test
    fun `recent speakers first, then an exact handle, then by name`() {
        assertEquals(
            listOf("Sam Recent", "Sam Earlier", "Zed", "Adam", "Sammy"),
            rank(
                "sam",
                candidate(1, "Sammy"),
                candidate(2, "Adam", username = "samuel"),
                candidate(3, "Zed", username = "sam"),
                candidate(4, "Sam Earlier", spokeAt = 10),
                candidate(5, "Sam Recent", spokeAt = 20),
            ),
        )
    }

    @Test
    fun `a recent speaker outranks an exact handle`() {
        assertEquals(
            listOf("Talker", "Exact"),
            rank("sam", candidate(1, "Exact", username = "sam"), candidate(2, "Talker", username = "samwise", spokeAt = 5)),
        )
    }

    @Test
    fun `the exact handle ignores case, diacritics and the mention trigger`() {
        assertEquals(
            listOf("Érica", "Alan"),
            rank("@ERICA", candidate(1, "Alan", username = "ericaz"), candidate(2, "Érica", username = "érica")),
        )
    }

    @Test
    fun `names sort by code point, so an emoji sorts above U+FFxx`() {
        // U+FFFD has no decomposition, so it survives folding. UTF-16 order would put the emoji's
        // high surrogate (U+D83D) first; code point order, like Swift's, puts U+1F600 last.
        assertEquals(
            listOf("\uFFFD Box", "\uD83D\uDE00 Smile"),
            rank("", candidate(1, "\uD83D\uDE00 Smile"), candidate(2, "\uFFFD Box")),
        )
    }

    @Test
    fun `the raw-name tiebreak compares code points too`() {
        // A full-width letter (U+FF21) against an emoji, as the raw names reach the tiebreak.
        assertTrue(CodePointOrder.compare("\uFF21", "\uD83D\uDE00") < 0)
        assertTrue("\uFF21" > "\uD83D\uDE00") // what String.compareTo would have said
        assertEquals(0, CodePointOrder.compare("abc", "abc"))
        assertTrue(CodePointOrder.compare("ab", "abc") < 0)
    }

    @Test
    fun `the last tie breaks on the user id as lowercase hex`() {
        // Signed bytes would put 0xAB (-85) before 0x0C (12); hex puts "0c" first.
        val low = candidate(0x0C, "Same")
        val high = candidate(0xAB, "Same")
        assertEquals(
            listOf(low.userId, high.userId),
            rankRosterMatches(listOf(high, low), emptyList()).map { it.userId },
        )
    }

    @Test
    fun `names sort with diacritics folded`() {
        assertEquals(
            listOf("Ana", "Érica", "Fran"),
            rank("", candidate(1, "Fran"), candidate(2, "Érica"), candidate(3, "Ana")),
        )
    }
}
