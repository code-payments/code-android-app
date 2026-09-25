package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flipcash.app.persistence.converters.ChatRulesSerialized
import com.flipcash.services.models.chat.MediaItem

@Entity(tableName = "chat_metadata")
data class ChatMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    @ColumnInfo(name = "chat_type")
    val chatType: String,
    @ColumnInfo(name = "last_activity_epoch_ms", index = true)
    val lastActivityEpochMs: Long,
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: Long?,
    @ColumnInfo(name = "latest_event_sequence", defaultValue = "0")
    val latestEventSequence: Long = 0,
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,
    // Highest message id already counted into the received-analytics people
    // properties. Monotonic; guards non-idempotent increments against replay.
    @ColumnInfo(name = "analytics_counted_through", defaultValue = "0")
    val analyticsCountedThrough: Long = 0,
    // Group identity. Null on a DM, where the counterparty's profile is the title and avatar.
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "picture_json")
    val pictureJson: MediaItem? = null,
    // The roster's true size and the version that decides whether a roster write applies.
    // Zero on a DM, and on any group whose metadata was rebuilt without a server round trip.
    @ColumnInfo(name = "member_count", defaultValue = "0")
    val memberCount: Long = 0,
    @ColumnInfo(name = "roster_version", defaultValue = "0")
    val rosterVersion: Long = 0,
    @ColumnInfo(name = "rules_json")
    val rulesJson: ChatRulesSerialized? = null,
    // Carried explicitly rather than inferred from the row's presence. Every group that reaches
    // the device today is one you are in, so this is constant for now — but membership is what
    // the gate, removal reconciliation and LeaveChat all turn on, and a state that important
    // should be readable in the table rather than implied by whether a row exists.
    @ColumnInfo(name = "is_member", defaultValue = "1")
    val isMember: Boolean = true,
    // The viewer's own mute, and the version that decides whether a viewer-state write applies.
    // A timed mute is stored as its deadline rather than as a muted flag: it lapses with no
    // server signal, so only the deadline compared against the clock on read stays correct.
    // Exactly two mute shapes exist — until a deadline, or forever — so the two columns are
    // never both set.
    @ColumnInfo(name = "mute_until_epoch_ms")
    val muteUntilEpochMs: Long? = null,
    @ColumnInfo(name = "mute_forever", defaultValue = "0")
    val muteForever: Boolean = false,
    @ColumnInfo(name = "viewer_state_version", defaultValue = "0")
    val viewerStateVersion: Long = 0,
    // The viewer's server-computed grants. Stored rather than left to the fetch that carried it
    // because every screen reads the viewer state back out of this row: a grant that lives only
    // in the response is already gone by the time anything draws. Defaults false, matching
    // ViewerState.Permissions — a column no write has reached yet denies rather than grants.
    @ColumnInfo(name = "can_edit", defaultValue = "0")
    val canEdit: Boolean = false,
    // Group creator; null for DMs and for group chats reconstructed without a server round
    // trip. Stored hex-encoded, matching every other user id column in this database.
    @ColumnInfo(name = "creator_hex")
    val creatorHex: String? = null,
    // Transitional E2EE flag (DMs only) -- see chat/v1 model.proto Metadata.use_e2ee. Ignored
    // behaviourally for now; carried so a chat rebuilt from Room agrees with the network value.
    @ColumnInfo(name = "use_e2ee", defaultValue = "0")
    val useE2ee: Boolean = false,
)
