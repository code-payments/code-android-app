package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The last answer a link in a message resolved to, so a card can be drawn resolved on the first
 * frame of the first visit after a cold start.
 *
 * Only cards whose size depends on the answer are stored: a group invite and a person's link. The
 * transcript is anchored at the bottom, so a card that grows when its lookup lands moves every
 * message above it. Cash and token cards are the same height in every state and are not kept here.
 *
 * [key] names the link, not the message: one group linked from ten messages is one row. [json] is
 * the chat layer's own snapshot of the card, serialized in `:apps:flipcash:features:messenger`
 * rather than by a type converter here, for the same reason [ChatDraftEntity] keeps its reply
 * target as a string: the shape belongs to the module that draws it.
 */
@Entity(tableName = "link_previews")
data class LinkPreviewEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "json")
    val json: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
