package com.flipcash.services.models.chat

/**
 * What UnarchiveChat answers on OK, including a no-op: the new [viewerState], and [chat] as GetChat
 * returns it, so the client can bring the chat back without a second read.
 */
data class UnarchivedChat(
    val viewerState: ViewerState,
    val chat: ChatMetadata?,
)
