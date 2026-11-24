package com.flipcash.services.models.chat

import kotlin.time.Instant

/**
 * The viewer archived this chat at [archivedAt]. Mirrors `chat.v1.ArchiveState`.
 *
 * Carried in [ViewerState.archive], present if and only if the chat is archived for the viewer,
 * and versioned with [ViewerState.version] like mute.
 */
data class ArchiveState(val archivedAt: Instant)
