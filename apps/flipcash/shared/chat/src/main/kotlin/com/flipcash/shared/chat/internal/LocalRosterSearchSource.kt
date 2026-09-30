package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatRosterDataSource
import com.flipcash.app.persistence.sources.RosterSearchCandidate
import com.flipcash.app.persistence.sources.search.MemberSearchText
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.RosterSearchSource
import javax.inject.Inject

/**
 * Searches the members held on the device, through the token index [ChatRosterDataSource] reads.
 *
 * A query of several words narrows: every word must start one of the member's words. The index
 * answers the longest word with a range scan and the rest are checked against the few members it
 * returns.
 */
internal class LocalRosterSearchSource @Inject constructor(
    private val rosterDataSource: ChatRosterDataSource,
    private val rosterSync: RosterSync,
    private val userManager: UserManager,
) : RosterSearchSource {

    override suspend fun search(chatId: ChatId, query: String, limit: Int): List<MemberMatch> {
        val selfId = userManager.accountId ?: return emptyList()
        val words = MemberSearchText.queryWords(query)

        val candidates = if (words.isEmpty()) {
            rosterDataSource.recentSpeakers(chatId, selfId, RECENT_MESSAGE_WINDOW)
        } else {
            rosterDataSource.searchByPrefix(chatId, selfId, words.maxBy { it.length }, RECENT_MESSAGE_WINDOW)
                .filter { candidate -> matchesEveryWord(candidate, words) }
        }

        return rankRosterMatches(candidates, words)
            .take(limit)
            .map { MemberMatch(it.userId, it.displayName, it.username, it.profilePicture) }
    }

    override suspend fun refresh(chatId: ChatId) {
        rosterSync.refreshFirstPage(chatId)
    }

    private fun matchesEveryWord(candidate: RosterSearchCandidate, words: List<String>): Boolean {
        if (words.size == 1) return true // the range scan already matched it
        val tokens = MemberSearchText.tokens(candidate.displayName, candidate.username)
        return words.all { word -> tokens.any { it.startsWith(word) } }
    }

    companion object {
        /** How many of a chat's newest held messages decide who counts as a recent speaker. */
        const val RECENT_MESSAGE_WINDOW = 50
    }
}

/**
 * Orders search results:
 * 1. members who sent one of the chat's newest held messages, most recent first;
 * 2. a member whose handle equals the whole query;
 * 3. everyone else, alphabetically by display name.
 *
 * Names compare in their normalized form, so "Érica" sorts with the e's. Ties fall back to the raw
 * name and then the user id, so an order never depends on the order rows came back in.
 */
internal fun rankRosterMatches(
    candidates: List<RosterSearchCandidate>,
    queryWords: List<String>,
): List<RosterSearchCandidate> {
    val wholeQuery = queryWords.joinToString(" ").takeIf { it.isNotEmpty() }
    fun isExactHandle(candidate: RosterSearchCandidate): Boolean =
        wholeQuery != null && candidate.username?.removePrefix("@")?.let(MemberSearchText::normalize) == wholeQuery

    val keyed = candidates.map { it to MemberSearchText.normalize(it.displayName) }
    return keyed.sortedWith(
        compareBy<Pair<RosterSearchCandidate, String>> { (candidate, _) -> candidate.lastSpokeEpochMs == null }
            .thenByDescending { (candidate, _) -> candidate.lastSpokeEpochMs ?: 0L }
            .thenBy { (candidate, _) -> !isExactHandle(candidate) }
            .thenBy { (_, name) -> name }
            .thenBy { (candidate, _) -> candidate.displayName }
            .thenBy { (candidate, _) -> candidate.userId.joinToString(",") }
    ).map { it.first }
}
