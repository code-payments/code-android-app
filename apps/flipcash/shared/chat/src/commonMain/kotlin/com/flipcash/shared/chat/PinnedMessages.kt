package com.flipcash.shared.chat

import com.flipcash.services.models.chat.Pin

/**
 * Every pin in a chat, most recently pinned first, and the `PinSummary.version` they were read at.
 * The version is what a later summary is compared against to know the list is out of date.
 */
data class PinnedMessages(
    val pins: List<Pin>,
    val version: Long,
)
