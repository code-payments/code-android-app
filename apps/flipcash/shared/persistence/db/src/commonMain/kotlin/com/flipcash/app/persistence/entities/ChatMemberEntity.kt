package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Relation
import com.flipcash.app.persistence.converters.MessagePointerSerialized

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chat_id_hex", "user_id_hex"],
)
data class ChatMemberEntity(
    @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "pointers_json") val pointersJson: List<MessagePointerSerialized>?,
)

/**
 * A chat member row joined to the shared, normalized [UserProfileEntity]. [profile] is
 * null until the member's profile has been synced (or, transiently, before the v26
 * migration backfill runs — the read mapper falls back to the staged blob in that case).
 */
data class ChatMemberWithProfile(
    @Embedded val member: ChatMemberEntity,
    @Relation(parentColumn = "user_id_hex", entityColumn = "user_id_hex")
    val profile: UserProfileEntity?,
)
