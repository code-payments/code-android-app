package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flipcash.app.persistence.entities.UserProfileEntity
import com.flipcash.services.models.chat.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    /**
     * Authoritative full-profile write (chat member sync). The caller has the complete
     * profile, so a whole-row replace is correct — and it clears any staged migration blob.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFull(profiles: List<UserProfileEntity>)

    /**
     * Partial write for callers that only know name + avatar (blocklist sync). Inserts a new
     * row, or updates *only* those two columns on an existing one — so it never downgrades a
     * richer profile already cached from a chat, and never clears a pending migration blob.
     *
     * Uses `INSERT OR REPLACE` with correlated sub-selects (rather than `ON CONFLICT DO
     * UPDATE`) so it works on the minSdk-29 SQLite build, which predates UPSERT. The
     * sub-selects read the current row before the replace, preserving every column the
     * blocklist doesn't know about; the avatar keeps its existing value when [profilePicture]
     * is null via COALESCE.
     */
    @Query(
        """
        INSERT OR REPLACE INTO user_profiles (
            user_id_hex, display_name, phone_value, phone_verified,
            email_value, email_verified, social_accounts_json,
            profile_picture_json, pending_migration_json
        ) VALUES (
            :userIdHex,
            :displayName,
            (SELECT phone_value FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT phone_verified FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT email_value FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT email_verified FROM user_profiles WHERE user_id_hex = :userIdHex),
            (SELECT social_accounts_json FROM user_profiles WHERE user_id_hex = :userIdHex),
            COALESCE(:profilePicture, (SELECT profile_picture_json FROM user_profiles WHERE user_id_hex = :userIdHex)),
            (SELECT pending_migration_json FROM user_profiles WHERE user_id_hex = :userIdHex)
        )
        """
    )
    suspend fun upsertNameAndAvatar(userIdHex: String, displayName: String, profilePicture: MediaItem?)

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
