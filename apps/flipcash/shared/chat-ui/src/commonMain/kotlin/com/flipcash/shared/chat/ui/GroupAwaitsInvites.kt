package com.flipcash.shared.chat.ui

/**
 * Whether a group is waiting for its first guests: a group, its roster loaded, the viewer the only
 * member, and the viewer able to invite. It decides both the "Your Group Is Ready" card in the
 * transcript and the "Invite people to get started" preview on the group's row in the chat list.
 *
 * Able to invite is `canInvite || !isPrivate`: the server withholds `can_invite` from a public
 * group's creator, yet group info's Share still opens the plain share sheet for them.
 *
 * [rosterResolved] is separate from [memberCount] because an unloaded group reads as a count of
 * zero or a stale one, and a prompt that flashed up before the roster arrived would leave again.
 */
fun groupAwaitsInvites(
    isGroup: Boolean,
    rosterResolved: Boolean,
    memberCount: Long,
    canInvite: Boolean,
    isPrivate: Boolean,
): Boolean = isGroup && rosterResolved && memberCount == 1L && (canInvite || !isPrivate)
