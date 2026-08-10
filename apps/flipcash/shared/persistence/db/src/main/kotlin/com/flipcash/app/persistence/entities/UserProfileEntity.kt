package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flipcash.app.persistence.converters.SocialAccountSerialized
import com.flipcash.services.models.chat.MediaItem

/**
 * A single cached copy of a user's public profile, keyed by user id.
 *
 * Previously the profile was serialized as a JSON blob and duplicated onto *every*
 * `chat_members` row (once per membership) and onto `blocked_users`. This table
 * normalizes it to one row per user, joined back via `@Relation` on `user_id_hex`
 * (see [ChatMemberWithProfile] / [BlockedUserWithProfile]).
 *
 * Scalar fields are decomposed into real columns. The two inherently-nested values
 * ([socialAccounts], [profilePicture]) stay serialized because modelling them as
 * columns would require child tables for no real query benefit.
 *
 * [pendingMigrationJson] is transient migration scaffolding: the v25→v26 migration
 * copies each legacy blob here verbatim (it cannot be decomposed in SQL), and a
 * one-shot backfill parses it into the columns and nulls it out. Reads fall back to
 * parsing it, so a not-yet-backfilled row still renders. New writes never set it.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "phone_value") val phoneValue: String?,
    @ColumnInfo(name = "phone_verified") val phoneVerified: Boolean?,
    @ColumnInfo(name = "email_value") val emailValue: String?,
    @ColumnInfo(name = "email_verified") val emailVerified: Boolean?,
    @ColumnInfo(name = "social_accounts_json") val socialAccounts: List<SocialAccountSerialized>?,
    @ColumnInfo(name = "profile_picture_json") val profilePicture: MediaItem?,
    @ColumnInfo(name = "pending_migration_json") val pendingMigrationJson: String? = null,
)
