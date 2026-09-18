package com.flipcash.services.models.chat

import kotlin.time.Instant

sealed interface MetadataUpdate {
    data class FullRefresh(val metadata: ChatMetadata) : MetadataUpdate
    data class LastActivityChanged(val newLastActivity: Instant) : MetadataUpdate
    // The receiving user's own state for the chat has changed (e.g. mute set/cleared).
    data class ViewerStateChanged(val viewerState: ViewerState) : MetadataUpdate
}
