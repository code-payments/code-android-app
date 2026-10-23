package com.flipcash.services.models.chat

/**
 * What a read intends to render, and so how much of a chat's messaging state the server may
 * return. Mirrors `messaging.v1.ViewMode`. The default, [FULL], is the contract that predates
 * redaction: full content, or DENIED.
 */
enum class ViewMode {
    /** Full content or nothing. Never returns a redacted message. */
    FULL,

    /** The most the viewer's standing allows: full when possible, redacted otherwise. */
    FULL_OR_REDACTED,

    /** Redacted content, always, for any viewer who may read the chat at all. */
    REDACTED,
}
