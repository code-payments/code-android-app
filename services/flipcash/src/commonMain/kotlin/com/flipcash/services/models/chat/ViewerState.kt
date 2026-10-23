package com.flipcash.services.models.chat

/**
 * Per-viewer state a chat holds about the requesting user, independent of membership. Mirrors
 * `chat.v1.ViewerState`. Always present for a member.
 */
data class ViewerState(
    // Present if and only if the chat is muted for the viewer.
    val mute: MuteState? = null,
    // Advanced by exactly one on every real change to this state. Compared like
    // RosterSummary.version: apply the greater value, drop the rest.
    val version: Long = 0,
    // Server-computed grants for the viewer. Never client-derivable; defaults false until
    // the server says otherwise.
    val permissions: Permissions = Permissions(),
) {
    data class Permissions(
        // Whether the viewer may call EditChat on this chat.
        val canEdit: Boolean = false,
    )
}
