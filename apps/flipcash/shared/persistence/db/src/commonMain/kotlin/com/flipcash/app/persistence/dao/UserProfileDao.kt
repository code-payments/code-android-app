package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.flipcash.app.persistence.entities.UserProfileEntity
import com.flipcash.services.models.chat.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    /** Whole-row replace, which also clears any staged migration blob. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFull(profiles: List<UserProfileEntity>)

    /**
     * Writes the profiles a chat's member list carries. A user's row is shared by every chat
     * they are in, but the server sends their phone number and email only in some of them (a
     * CONTACT_DM member carries the number, the same user as a TIP_DM member does not). So a
     * member without a phone or email keeps the one already stored instead of erasing it; every
     * other column is replaced, and the staged migration blob is cleared as [upsertFull] does.
     */
    @Transaction
    suspend fun upsertMembers(profiles: List<UserProfileEntity>) {
        upsertFull(
            profiles.map { incoming ->
                val stored = getByUserId(incoming.userIdHex) ?: return@map incoming
                incoming.copy(
                    phoneValue = incoming.phoneValue ?: stored.phoneValue,
                    phoneVerified = if (incoming.phoneValue != null) incoming.phoneVerified else stored.phoneVerified,
                    emailValue = incoming.emailValue ?: stored.emailValue,
                    emailVerified = if (incoming.emailValue != null) incoming.emailVerified else stored.emailVerified,
                )
            }
        )
    }

    /**
     * Partial write for callers that only know a user's public identity — name, avatar and handle
     * (blocklist sync, a single `GetProfile`). Inserts a new row, or updates *only* those three
     * columns on an existing one — so it never downgrades a richer profile already cached from a
     * chat, and never clears a pending migration blob.
     *
     * Uses `INSERT OR REPLACE` with correlated sub-selects (rather than `ON CONFLICT DO
     * UPDATE`) so it works on the minSdk-29 SQLite build, which predates UPSERT. The
     * sub-selects read the current row before the replace, preserving every column the
     * blocklist doesn't know about. The name, avatar and handle keep their existing values when
     * [displayName], [profilePicture] or [username] is null, via COALESCE: null means the caller has no
     * profile to write (a failed fetch), whereas an empty name is one the user really has. A
     * null name with nothing cached stores an empty one, since the column is NOT NULL.
     */
    @Query(
        """
        INSERT OR REPLACE INTO user_profiles (
            user_id_hex, display_name, phone_value, phone_verified,
            email_value, email_verified, social_accounts_json,
            profile_picture_json, username, pending_migration_json
        ) VALUES (
            :userIdHex,
            COALESCE(:displayName, (SELECT display_name FROM user_profiles WHERE user_id_hex = :userIdHex), ''),
            (SELECT phone_value FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT phone_verified FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT email_value FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT email_verified FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT social_accounts_json FROM user_profiles WHERE user_id_hex = :userIdHex),
            COALESCE(:profilePicture, (SELECT profile_picture_json FROM user_profiles WHERE user_id_hex = :userIdHex)),
            COALESCE(:username, (SELECT username FROM user_profiles WHERE user_id_hex = :userIdHex)),
            (SELECT pending_migration_json FROM user_profiles WHERE user_id_hex = :userIdHex)
        )
        """
    )
    suspend fun upsertNameAndAvatar(
        userIdHex: String,
        displayName: String?,
        profilePicture: MediaItem?,
        username: String?,
    )

    /** The cached profile for [userIdHex], or null if none is cached. */
    @Query("SELECT * FROM user_profiles WHERE user_id_hex = :userIdHex LIMIT 1")
    suspend fun getByUserId(userIdHex: String): UserProfileEntity?

    /** Observes every cached profile; re-emits as profiles are added/updated. */
    @Query("SELECT * FROM user_profiles")
    fun observeAll(): Flow<List<UserProfileEntity>>

    /** A batch of rows still carrying a staged legacy blob; drives [backfillMigratedProfiles]. */
    @Query("SELECT * FROM user_profiles WHERE pending_migration_json IS NOT NULL LIMIT :limit")
    suspend fun pendingMigrationBatch(limit: Int): List<UserProfileEntity>

    @Update
    suspend fun update(profiles: List<UserProfileEntity>)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAll()
}
