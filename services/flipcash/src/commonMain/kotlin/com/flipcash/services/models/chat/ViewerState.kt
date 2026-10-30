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
    // Present if and only if a moderator restricts the viewer in this chat. While a silence is
    // set the composer is hidden; the client owns the countdown for a timed silence.
    val restrictions: Restrictions? = null,
) {
    data class Permissions(
        // Whether the viewer may call EditChat on this chat.
        val canEdit: Boolean = false,
        // Whether the viewer may create, list and revoke invite links. True today only for a
        // private group's creator while a member.
        val canInvite: Boolean = false,
        // Whether the viewer may call PinMessage / UnpinMessage on this chat.
        val canPin: Boolean = false,
        // Which moderation actions the viewer may take on other users.
        val moderation: ModerationPermissions = ModerationPermissions(),
    )

    data class ModerationPermissions(
        // Whether the viewer may call SilenceUser / UnsilenceUser.
        val canSilence: Boolean = false,
        // Whether the viewer may call BanUser / UnbanUser.
        val canBan: Boolean = false,
    )

    data class Restrictions(
        val silence: SilenceState? = null,
    )
}
