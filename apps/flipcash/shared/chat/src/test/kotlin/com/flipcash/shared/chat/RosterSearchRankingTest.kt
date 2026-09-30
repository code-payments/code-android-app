package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.RosterSearchCandidate
import com.flipcash.app.persistence.sources.search.MemberSearchText
import com.flipcash.shared.chat.internal.rankRosterMatches
import org.junit.Test
import kotlin.test.assertEquals

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
    fun `names sort with diacritics folded`() {
        assertEquals(
            listOf("Ana", "Érica", "Fran"),
            rank("", candidate(1, "Fran"), candidate(2, "Érica"), candidate(3, "Ana")),
        )
    }
}
