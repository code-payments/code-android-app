package com.flipcash.services.models.chat

import kotlin.time.Instant

sealed interface MetadataUpdate {
    data class FullRefresh(val metadata: ChatMetadata) : MetadataUpdate
    data class LastActivityChanged(val newLastActivity: Instant) : MetadataUpdate
}
