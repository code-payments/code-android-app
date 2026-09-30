package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.dao.MemberSearchRow
import com.flipcash.app.persistence.entities.ChatRosterSyncEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.app.persistence.sources.search.MemberSearchText
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a chat's member search index, and records how much of each group's roster has been read
 * into it. The index itself is written by [ChatMemberDataSource] alongside the members.
 */
@Singleton
class ChatRosterDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    /**
     * Whether the signed-in user's database is open. A worker can start in a process that has not
     * opened it yet, and a read with nowhere to go should be retried rather than recorded.
     */
    val isAvailable: Boolean
        get() = db != null

    /**
     * Members of [chatId] other than [selfId] with a name or handle word starting with [prefix],
     * which must already be normalized with [MemberSearchText.normalize]. Unordered.
     */
    suspend fun searchByPrefix(
        chatId: ChatId,
        selfId: ID,
        prefix: String,
        recentWindow: Int,
    ): List<RosterSearchCandidate> {
        val dao = db?.chatMemberSearchDao() ?: return emptyList()
        return dao.searchByTokenRange(
            chatIdHex = mapper.chatIdHex(chatId),
            selfIdHex = mapper.userIdHex(selfId),
            lower = prefix,
            upper = prefix + MemberSearchText.UPPER_BOUND,
            recentWindow = recentWindow,
        ).map { it.toCandidate() }
    }

    /** Members of [chatId] other than [selfId] who sent one of its newest [recentWindow] held messages. */
    suspend fun recentSpeakers(chatId: ChatId, selfId: ID, recentWindow: Int): List<RosterSearchCandidate> {
        val dao = db?.chatMemberSearchDao() ?: return emptyList()
        return dao.recentSpeakers(
            chatIdHex = mapper.chatIdHex(chatId),
            selfIdHex = mapper.userIdHex(selfId),
            recentWindow = recentWindow,
        ).map { it.toCandidate() }
    }

    suspend fun getSyncState(chatId: ChatId): RosterSyncState? =
        db?.chatMemberSearchDao()?.getSyncState(mapper.chatIdHex(chatId))?.let {
            RosterSyncState(
                watermark = it.watermark,
                fullySynced = it.fullySynced,
                truncated = it.truncated,
                reconcilePending = it.reconcilePending,
            )
        }

    /**
     * Records a full read of [chatId]'s roster that ended with [watermark] as the version it can
     * vouch for, clearing any pending reconcile.
     */
    suspend fun markFullySynced(chatId: ChatId, watermark: Long, truncated: Boolean) {
        db?.chatMemberSearchDao()?.upsertSyncState(
            ChatRosterSyncEntity(
                chatIdHex = mapper.chatIdHex(chatId),
                watermark = watermark,
                fullySynced = true,
                truncated = truncated,
                reconcilePending = false,
            )
        )
    }

    /** Sets [chatId]'s watermark after a catch-up read confirmed the roster at [watermark]. */
    suspend fun setWatermark(chatId: ChatId, watermark: Long) {
        db?.chatMemberSearchDao()?.setWatermark(mapper.chatIdHex(chatId), watermark)
    }

    /** Moves [chatId]'s watermark to [to] only if it is at [from]; see the DAO for why. */
    suspend fun advanceWatermark(chatId: ChatId, from: Long, to: Long) {
        db?.chatMemberSearchDao()?.advanceWatermark(mapper.chatIdHex(chatId), from, to)
    }

    /** Flags [chatId] as holding members who may have left, until a full read settles it. */
    suspend fun markReconcilePending(chatId: ChatId) {
        db?.chatMemberSearchDao()?.setReconcilePending(mapper.chatIdHex(chatId))
    }

    private fun MemberSearchRow.toCandidate() = RosterSearchCandidate(
        userId = mapper.userIdFromHex(userIdHex),
        displayName = displayName.orEmpty(),
        username = username,
        profilePicture = profilePicture,
        lastSpokeEpochMs = lastSpokeEpochMs,
    )
}

/** A member a roster search found, before ranking. */
data class RosterSearchCandidate(
    val userId: ID,
    val displayName: String,
    val username: String?,
    val profilePicture: MediaItem?,
    // When they last sent one of the chat's newest held messages; null if they sent none of them.
    val lastSpokeEpochMs: Long?,
)

data class RosterSyncState(
    val watermark: Long,
    val fullySynced: Boolean,
    val truncated: Boolean,
    val reconcilePending: Boolean,
)
