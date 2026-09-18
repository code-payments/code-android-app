package com.flipcash.services.models.chat

/**
 * Per-viewer state a chat holds about the requesting user, independent of membership. Mirrors
 * `chat.v1.ViewerState`.
 */
data class ViewerState(
    // Present if and only if the chat is muted for the viewer.
    val mute: MuteState? = null,
    // Advanced by exactly one on every real change to this state. Compared like
    // RosterSummary.version: apply the greater value, drop the rest.
    val version: Long = 0,
)
