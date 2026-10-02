package com.flipcash.shared.chat

/**
 * Whether a push for a chat may post a notification.
 *
 * - **Muted: never**, archived or not. Mute already suppresses every push and archiving must not
 *   add a way through it (rule 2).
 * - **Not archived and not muted: always.** Archive adds a filter; it never changes what an
 *   ordinary chat does.
 * - **Archived, not muted: only a message addressed to the viewer**, which is an @mention of them
 *   or a reply to one of their messages (rule 3). The message's kind does not matter: received
 *   cash, media and system messages do not break through on their own, because in a tip DM most
 *   messages may be cash and letting them through would make archive useless where people most
 *   want quiet.
 *
 * [mentionsViewer] and [repliesToViewer] are false when the device cannot tell: the message text
 * is not available, a mention names a handle that is not the viewer's, or the replied-to message
 * is not stored locally. Both platforms default the same way.
 *
 * Pure: no Android types. The fixture's `notify` cases drive this directly.
 */
fun shouldNotify(
    archived: Boolean,
    muted: Boolean,
    mentionsViewer: Boolean,
    repliesToViewer: Boolean,
): Boolean = when {
    muted -> false
    !archived -> true
    else -> mentionsViewer || repliesToViewer
}
