package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.LobbyMember
import com.getcode.opencode.model.core.ID

/** Whether [selfId] created this private group, so its lobby is theirs to answer. */
fun ChatMetadata.isHostedBy(selfId: ID?): Boolean = selfId != null && isPrivate && creator == selfId

/** Private groups [selfId] created, by id, with their titles. */
fun hostedPrivateGroups(summaries: List<ChatSummary>, selfId: ID?): Map<ChatId, String> =
    summaries
        .map { it.metadata }
        .filter { it.isHostedBy(selfId) }
        .associate { it.chatId to it.title.orEmpty() }

/**
 * Who waits to join [metadata]'s group, newest first, when [selfId] hosts it; empty for any other
 * chat, and for a hosted group whose lobby isn't loaded.
 */
fun Map<ChatId, List<LobbyMember>>.waitingFor(metadata: ChatMetadata, selfId: ID?): List<LobbyMember> =
    if (metadata.isHostedBy(selfId)) this[metadata.chatId].orEmpty() else emptyList()
