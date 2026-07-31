package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flipcash.app.persistence.converters.UserProfileSerialized

/**
 * A single user on the current account's blocklist, cached for offline display.
 *
 * The server's blocklist entry only carries the user id + when they were blocked, so the
 * display profile ([userProfileJson], resolved separately when the page is fetched) is embedded
 * here — mirroring how [ChatMemberEntity] embeds a member's profile — so the list renders name +
 * avatar without a per-row network lookup.
 */
@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "blocked_at_epoch_ms") val blockedAtEpochMs: Long,
    @ColumnInfo(name = "user_profile_json") val userProfileJson: UserProfileSerialized?,
)
