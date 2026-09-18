package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMetadata

/**
 * A chat's metadata together with whether this device is a member of it.
 *
 * [ChatMetadata] is server truth about the chat; membership is a fact about the viewer, stored on
 * the same row and written by a join, a leave or a feed sync. The access gate needs both to decide
 * what it renders, and a caller that had to ask twice could observe them a frame apart and blur a
 * chat it had just joined.
 */
data class ChatMembership(
    val metadata: ChatMetadata,
    /**
     * Whether the viewer is in this chat, or `null` when nothing local answers that yet.
     *
     * Only [MessagingOperations.hydrateChat] produces `null`: it fetches a chat the device holds no
     * row for, and `GetChat` returns the chat without the caller's relationship to it, so an
     * unsynced chat has no answer rather than a negative one. Callers deciding what the viewer may
     * *do* may read it as "not a member". Callers deciding what the viewer may *see* must withhold,
     * because an unknown rendered as a member shows the transcript the gate exists to withhold.
     */
    val isMember: Boolean?,
)
