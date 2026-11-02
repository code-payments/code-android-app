package com.flipcash.shared.chat.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MentionSuggestion
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.RosterSearchSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Searches the pool `Chat.GetMentionSuggestions` returns for a group, held by [pool].
 *
 * The pool is filtered locally with [MentionText]'s word-prefix rule and keeps the server's
 * order: a bare `@` shows its head. When the fetch fails (DENIED, NOT_FOUND, or the call never
 * reached the server) the result is empty, with no fallback; the user can still type a handle by hand.
 */
internal class ServerRosterSearchSource(
    private val pool: MentionSuggestionPool,
    private val userManager: UserManager,
    private val scope: CoroutineScope,
) : RosterSearchSource {

    @Inject constructor(
        pool: MentionSuggestionPool,
        userManager: UserManager,
    ) : this(pool, userManager, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    override suspend fun search(chatId: ChatId, query: String, limit: Int): List<MemberMatch> {
        val held = pool.pool(scope, chatId).getOrElse { return emptyList() }
        val selfId = userManager.accountId
        val words = MentionText.queryWords(query)
        return held.asSequence()
            .filter { it.userProfile.userId != null && it.userProfile.userId != selfId }
            .filter { words.isEmpty() || matchesEveryWord(it, words) }
            .take(limit)
            .map { it.toMatch() }
            .toList()
    }

    // A new composing session: fetch the pool again.
    override suspend fun refresh(chatId: ChatId) {
        pool.startSession(scope, chatId)
    }

    private fun matchesEveryWord(suggestion: MentionSuggestion, words: List<String>): Boolean {
        val tokens = MentionText.tokens(suggestion.userProfile.displayName, suggestion.userProfile.username)
        return words.all { word -> tokens.any { it.startsWith(word) } }
    }

    private fun MentionSuggestion.toMatch() = MemberMatch(
        userId = userProfile.userId!!,
        displayName = userProfile.displayName,
        username = userProfile.username,
        profilePicture = userProfile.profilePicture,
    )
}
