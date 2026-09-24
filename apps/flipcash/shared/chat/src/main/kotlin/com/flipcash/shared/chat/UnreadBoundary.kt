package com.flipcash.shared.chat

/**
 * Where the "N unread" divider sits in a chat transcript, resolved once per visit from the viewer's
 * own READ pointer as stored when the chat opened.
 */
sealed interface UnreadBoundary {
    /** Not read yet; the transcript holds read reporting and initial positioning until it resolves. */
    data object Resolving : UnreadBoundary

    /** No divider: nothing inbound is unread, or the viewer's own member row is not stored. */
    data object None : UnreadBoundary

    /**
     * The divider sits in the gap straddling [readThrough]: the viewer's READ pointer, moved past any
     * of their own messages that directly follow it, so the divider lands above the first message
     * someone else sent. It need not be a stored message's id. [count] is the inbound, non-deleted
     * messages past the pointer.
     */
    data class At(val readThrough: Long, val count: Int) : UnreadBoundary
}
