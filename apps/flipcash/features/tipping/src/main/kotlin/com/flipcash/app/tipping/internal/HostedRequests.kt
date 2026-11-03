package com.flipcash.app.tipping.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LobbyMember

/**
 * The hosted groups with someone waiting, each with its waiting list, the group whose newest
 * request is the most recent first.
 */
internal fun hostedRequests(
    hosted: Collection<ChatId>,
    lobbies: Map<ChatId, List<LobbyMember>>,
): List<Pair<ChatId, List<LobbyMember>>> = hosted
    .mapNotNull { chatId -> lobbies[chatId]?.takeIf { it.isNotEmpty() }?.let { chatId to it } }
    .sortedByDescending { (_, waiting) -> waiting.maxOf { it.enteredAt } }
