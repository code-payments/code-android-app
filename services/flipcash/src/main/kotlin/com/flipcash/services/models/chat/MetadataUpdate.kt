package com.flipcash.services.models.chat

import kotlin.time.Instant

sealed interface MetadataUpdate {
    data class FullRefresh(val metadata: ChatMetadata) : MetadataUpdate
    data class LastActivityChanged(val newLastActivity: Instant) : MetadataUpdate
    // The receiving user's own state for the chat has changed (e.g. mute set/cleared).
    data class ViewerStateChanged(val viewerState: ViewerState) : MetadataUpdate
    // A group chat's title was edited. Best-effort delivery: applied as received, no ordering
    // guaranteed against other updates for the same chat.
    data class TitleChanged(val newTitle: String) : MetadataUpdate
    // A group chat's picture was edited. Best-effort delivery, same caveat as TitleChanged.
    data class PictureChanged(val newPicture: MediaItem) : MetadataUpdate
}
