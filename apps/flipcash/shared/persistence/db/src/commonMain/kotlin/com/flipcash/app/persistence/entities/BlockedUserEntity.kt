package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A single user on the current account's blocklist, cached for offline display.
 *
 * The server's blocklist entry only carries the user id + when they were blocked; the
 * display profile (name + avatar) lives in the shared, normalized [UserProfileEntity]
 * and is joined via [BlockedUserWithProfile] so the list renders without a per-row
 * network lookup.
 */
@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "blocked_at_epoch_ms") val blockedAtEpochMs: Long,
)

/**
 * A blocklist row joined to the shared, normalized [UserProfileEntity]. [profile] may be
 * null if the user's profile hasn't been cached yet; the read mapper falls back to
 * empty display data in that case.
 */
data class BlockedUserWithProfile(
    @Embedded val blocked: BlockedUserEntity,
    @Relation(parentColumn = "user_id_hex", entityColumn = "user_id_hex")
    val profile: UserProfileEntity?,
)
