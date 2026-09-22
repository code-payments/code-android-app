package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.converters.ChatRulesSerialized
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.models.chat.MediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMetadataDao {

    @Query("SELECT * FROM chat_metadata ORDER BY last_activity_epoch_ms DESC")
    fun observeAll(): Flow<List<ChatMetadataEntity>>

    /**
     * The merged conversation list, as a Paging source.
     *
     * One ordering across every type in [chatTypes] — a group ranks against DMs by activity like
     * anything else, so ordering per type and interleaving afterwards would put it in the wrong
     * place. `chat_id_hex` breaks a tie: Paging asks for the next page by offset, and an order
     * that is not total lets two rows swap between page loads, which duplicates one and loses the
     * other.
     *
     * Chats you are not in are filtered here rather than in the projection so they do not consume
     * a page slot.
     */
    @Query(
        "SELECT * FROM chat_metadata WHERE chat_type IN (:chatTypes) " +
            "AND is_hidden = 0 AND is_member = 1 " +
            "ORDER BY last_activity_epoch_ms DESC, chat_id_hex DESC"
    )
    fun observeFeedPaged(chatTypes: List<String>): PagingSource<Int, ChatMetadataEntity>

    /** The chats of [chatType] this device still considers you a member of. */
    @Query("SELECT chat_id_hex FROM chat_metadata WHERE chat_type = :chatType AND is_member = 1")
    suspend fun getChatIdsOfType(chatType: String): List<String>

    /**
     * Sets membership without disturbing the rest of the row. Leaving a group keeps its title,
     * rules and transcript — the gate reads them to decide what a non-member is allowed to see.
     */
    @Query("UPDATE chat_metadata SET is_member = :isMember WHERE chat_id_hex = :chatIdHex")
    suspend fun updateMembership(chatIdHex: String, isMember: Boolean)

    /** The roster version already applied to [chatIdHex], or null when the chat is not stored. */
    @Query("SELECT roster_version FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getRosterVersion(chatIdHex: String): Long?

    @Query("SELECT * FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getById(chatIdHex: String): ChatMetadataEntity?

    /**
     * One chat's row, re-emitted whenever it changes.
     *
     * The chrome and the access gate read title, picture, member count, rules and membership off
     * this row, and every one of them moves under the user — a roster event changes the count, a
     * join flips membership. Room re-runs the query on any write to `chat_metadata`, so a screen
     * observing this needs nothing that polls.
     *
     * Emits `null` for a chat this device has not stored, which is the state a chat opened from a
     * link sits in until its sync lands.
     */
    @Query("SELECT * FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    fun observeById(chatIdHex: String): Flow<ChatMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ChatMetadataEntity): Long

    /**
     * Overwrites only the columns the server owns. `latest_event_sequence` and
     * `analytics_counted_through` are client-owned watermarks that no server payload
     * carries, so they are deliberately absent here. The roster and viewer-state columns
     * are absent too — they are versioned, and go through [updateRosterIfNewer] and
     * [updateViewerStateIfNewer].
     */
    @Query(
        "UPDATE chat_metadata SET chat_type = :chatType, " +
            "last_activity_epoch_ms = :lastActivityEpochMs, " +
            "last_message_id = :lastMessageId, " +
            "is_hidden = :isHidden, " +
            "title = :title, " +
            "picture_json = :pictureJson, " +
            "rules_json = :rulesJson, " +
            "is_member = :isMember " +
            "WHERE chat_id_hex = :chatIdHex"
    )
    suspend fun updateServerOwnedFields(
        chatIdHex: String,
        chatType: String,
        lastActivityEpochMs: Long,
        lastMessageId: Long?,
        isHidden: Boolean,
        title: String?,
        pictureJson: MediaItem?,
        rulesJson: ChatRulesSerialized?,
        isMember: Boolean,
    )

    /**
     * Applies a roster snapshot only when it is strictly newer than the stored one.
     *
     * `RosterSummary.version` advances by exactly one on every membership change, so a write
     * carrying an older or equal version has nothing new to say. Guarding on it here is what
     * stops a `ChatMetadata` rebuilt from the database — which reports version 0 — from
     * clobbering a real member count on its way back through an upsert.
     */
    @Query(
        "UPDATE chat_metadata SET member_count = :memberCount, roster_version = :rosterVersion " +
            "WHERE chat_id_hex = :chatIdHex AND :rosterVersion > roster_version"
    )
    suspend fun updateRosterIfNewer(chatIdHex: String, memberCount: Long, rosterVersion: Long)

    /** The viewer-state version already applied to [chatIdHex], or null when the chat is not stored. */
    @Query("SELECT viewer_state_version FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getViewerStateVersion(chatIdHex: String): Long?

    /**
     * Applies a viewer state only when it is strictly newer than the stored one.
     *
     * `ViewerState.version` is what makes the state converge: stream delivery is not ordered, so
     * a mute and the unmute that followed it can arrive either way round, and applying the last
     * one to land would leave the wrong answer stored. Guarding on the version is also what stops
     * a [ChatMetadataEntity] rebuilt from this row — which reports the version it was read at —
     * from re-applying itself on the way back through an upsert.
     *
     * Both mute columns are written together so a switch between the two shapes clears the other.
     *
     * The viewer's grants ride the same gate. They are part of the same server-owned state and
     * carry no version of their own, so splitting them out would let a stale payload reinstate a
     * grant the newest one had withdrawn.
     */
    @Query(
        "UPDATE chat_metadata SET mute_until_epoch_ms = :muteUntilEpochMs, " +
            "mute_forever = :muteForever, viewer_state_version = :version, " +
            "can_edit = :canEdit " +
            "WHERE chat_id_hex = :chatIdHex AND :version > viewer_state_version"
    )
    suspend fun updateViewerStateIfNewer(
        chatIdHex: String,
        muteUntilEpochMs: Long?,
        muteForever: Boolean,
        canEdit: Boolean,
        version: Long,
    )

    /**
     * Drops the viewer state, version and all.
     *
     * Leaving a chat clears the mute server-side, and the server sends nothing to say so. The
     * version goes back to zero with it: a rejoin starts the server's numbering over, so a
     * retained version would make the gate reject the new state and the old mute would survive a
     * chat the user has left and re-entered.
     *
     * The grants go with it. A chat the user is no longer in grants nothing, and leaving one
     * while holding an edit right would otherwise keep the affordance on screen.
     */
    @Query(
        "UPDATE chat_metadata SET mute_until_epoch_ms = NULL, mute_forever = 0, " +
            "viewer_state_version = 0, can_edit = 0 WHERE chat_id_hex = :chatIdHex"
    )
    suspend fun clearViewerState(chatIdHex: String)

    /**
     * Inserts a new chat, or refreshes an existing one's server-owned columns in place.
     *
     * Deliberately not a whole-row REPLACE: `latest_event_sequence` is the cursor the
     * client has actually applied from the event log, and `analytics_counted_through`
     * is the replay guard for received-message analytics. Neither is carried by a feed
     * payload, so replacing the row would reset both — the cursor to whatever the server
     * reported as its head (skipping every unapplied event on the next `GetDelta`) and
     * the analytics watermark to zero (re-counting messages already counted).
     */
    @Transaction
    suspend fun upsert(entity: ChatMetadataEntity) {
        if (insertIfAbsent(entity) != -1L) return
        updateServerOwnedFields(
            chatIdHex = entity.chatIdHex,
            chatType = entity.chatType,
            lastActivityEpochMs = entity.lastActivityEpochMs,
            lastMessageId = entity.lastMessageId,
            isHidden = entity.isHidden,
            title = entity.title,
            pictureJson = entity.pictureJson,
            rulesJson = entity.rulesJson,
            isMember = entity.isMember,
        )
        updateRosterIfNewer(
            chatIdHex = entity.chatIdHex,
            memberCount = entity.memberCount,
            rosterVersion = entity.rosterVersion,
        )
        updateViewerStateIfNewer(
            chatIdHex = entity.chatIdHex,
            muteUntilEpochMs = entity.muteUntilEpochMs,
            muteForever = entity.muteForever,
            canEdit = entity.canEdit,
            version = entity.viewerStateVersion,
        )
    }

    @Transaction
    suspend fun upsert(entities: List<ChatMetadataEntity>) {
        for (entity in entities) upsert(entity)
    }

    @Query("SELECT last_activity_epoch_ms FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getLastActivity(chatIdHex: String): Long?

    @Query("UPDATE chat_metadata SET last_activity_epoch_ms = :epochMs WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLastActivity(chatIdHex: String, epochMs: Long)

    @Query("SELECT last_message_id FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getLastMessageId(chatIdHex: String): Long?

    @Query("UPDATE chat_metadata SET last_message_id = :messageId WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLastMessageId(chatIdHex: String, messageId: Long)

    @Query("UPDATE chat_metadata SET latest_event_sequence = :sequence WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLatestEventSequence(chatIdHex: String, sequence: Long)

    @Query("SELECT latest_event_sequence FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getLatestEventSequence(chatIdHex: String): Long?

    @Query("SELECT chat_type FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getChatType(chatIdHex: String): String?

    /**
     * A group's name, or null when the chat is not stored or is a DM.
     *
     * The two nulls read the same here on purpose: a DM has no title to return, so a caller that
     * wants one is asking about a group either way.
     */
    @Query("SELECT title FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getTitle(chatIdHex: String): String?

    @Query("SELECT analytics_counted_through FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getAnalyticsCountedThrough(chatIdHex: String): Long?

    // MAX() keeps the watermark monotonic even if an out-of-order write lands.
    @Query(
        "UPDATE chat_metadata SET analytics_counted_through = MAX(analytics_counted_through, :messageId) " +
            "WHERE chat_id_hex = :chatIdHex"
    )
    suspend fun advanceAnalyticsCountedThrough(chatIdHex: String, messageId: Long)

    @Query("UPDATE chat_metadata SET is_hidden = :hidden WHERE chat_id_hex = :chatIdHex")
    suspend fun updateHidden(chatIdHex: String, hidden: Boolean)

    /**
     * Applied unconditionally, unlike [updateRosterIfNewer] and [updateViewerStateIfNewer]:
     * `MetadataUpdate.TitleChanged` carries no version, so there is nothing to gate on. Delivery
     * is best-effort; a client that suspects a miss refetches via `GetChat` rather than relying
     * on this to converge on its own.
     */
    @Query("UPDATE chat_metadata SET title = :title WHERE chat_id_hex = :chatIdHex")
    suspend fun updateTitle(chatIdHex: String, title: String)

    /** Unconditional, for the same reason as [updateTitle]: `MetadataUpdate.PictureChanged` carries no version. */
    @Query("UPDATE chat_metadata SET picture_json = :pictureJson WHERE chat_id_hex = :chatIdHex")
    suspend fun updatePicture(chatIdHex: String, pictureJson: MediaItem)

    @Query("DELETE FROM chat_metadata")
    suspend fun deleteAll()
}
