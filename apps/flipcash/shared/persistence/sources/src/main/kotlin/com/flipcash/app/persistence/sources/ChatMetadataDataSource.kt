package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMetadataDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeAll(): Flow<List<ChatMetadataEntity>> =
        db?.chatMetadataDao()?.observeAll() ?: emptyFlow()

    /** One chat's row by [chatId], re-emitted on change; `null` while it is not stored. */
    fun observeById(chatId: ChatId): Flow<ChatMetadataEntity?> =
        db?.chatMetadataDao()?.observeById(mapper.chatIdHex(chatId)) ?: emptyFlow()

    /**
     * The merged feed of [chatTypes] as a Paging source.
     *
     * Unlike every other read here, a closed database cannot answer with an empty result: Paging
     * would present that as "no conversations" and stop asking. An error keeps the list in its
     * loading state and lets the caller retry once the database is open.
     */
    fun observeFeedPaged(chatTypes: List<ChatType>): PagingSource<Int, ChatMetadataEntity> =
        db?.chatMetadataDao()?.observeFeedPaged(chatTypes.map { it.name })
            ?: emptyPagingSource()

    /** The chats of [chatType] this device still considers the user a member of, keyed by hex. */
    suspend fun getChatIdsOfType(chatType: ChatType): List<String> =
        db?.chatMetadataDao()?.getChatIdsOfType(chatType.name).orEmpty()

    suspend fun setMembership(chatId: ChatId, isMember: Boolean) {
        setMembership(mapper.chatIdHex(chatId), isMember)
    }

    suspend fun setMembership(chatIdHex: String, isMember: Boolean) {
        db?.chatMetadataDao()?.updateMembership(chatIdHex, isMember)
    }

    /** The roster version already applied to [chatId]; 0 when the chat is not stored yet. */
    suspend fun getRosterVersion(chatId: ChatId): Long =
        db?.chatMetadataDao()?.getRosterVersion(mapper.chatIdHex(chatId)) ?: 0L

    suspend fun updateRoster(chatId: ChatId, memberCount: Long, rosterVersion: Long) {
        db?.chatMetadataDao()?.updateRosterIfNewer(
            chatIdHex = mapper.chatIdHex(chatId),
            memberCount = memberCount,
            rosterVersion = rosterVersion,
        )
    }

    /** The viewer-state version already applied to [chatId]; 0 when the chat is not stored yet. */
    suspend fun getViewerStateVersion(chatId: ChatId): Long =
        db?.chatMetadataDao()?.getViewerStateVersion(mapper.chatIdHex(chatId)) ?: 0L

    /**
     * Stores [viewerState] if its version beats the stored one, and drops it otherwise. Stream
     * delivery is unordered, so the newest state is the one with the highest version, not the one
     * that arrived last.
     */
    suspend fun updateViewerState(chatId: ChatId, viewerState: ViewerState) {
        db?.chatMetadataDao()?.updateViewerStateIfNewer(
            chatIdHex = mapper.chatIdHex(chatId),
            muteUntilEpochMs = mapper.muteUntilEpochMs(viewerState.mute),
            muteForever = mapper.isMuteForever(viewerState.mute),
            version = viewerState.version,
        )
    }

    /** Forgets the viewer state for [chatId], version included. Leaving a chat clears its mute. */
    suspend fun clearViewerState(chatId: ChatId) {
        db?.chatMetadataDao()?.clearViewerState(mapper.chatIdHex(chatId))
    }

    /** The hex a [chatId] is keyed by. Callers comparing against [getChatIdsOfType] need it. */
    fun chatIdHex(chatId: ChatId): String = mapper.chatIdHex(chatId)

    suspend fun upsert(metadata: ChatMetadata) {
        db?.chatMetadataDao()?.upsert(mapper.toEntity(metadata))
    }

    suspend fun upsert(metadatas: List<ChatMetadata>) {
        db?.chatMetadataDao()?.upsert(metadatas.map { mapper.toEntity(it) })
    }

    suspend fun getLastActivity(chatId: ChatId): Long? =
        db?.chatMetadataDao()?.getLastActivity(mapper.chatIdHex(chatId))

    suspend fun updateLastActivity(chatId: ChatId, epochMs: Long) {
        db?.chatMetadataDao()?.updateLastActivity(mapper.chatIdHex(chatId), epochMs)
    }

    suspend fun getLastMessageId(chatId: ChatId): Long? =
        db?.chatMetadataDao()?.getLastMessageId(mapper.chatIdHex(chatId))

    suspend fun updateLastMessageId(chatId: ChatId, messageId: Long) {
        db?.chatMetadataDao()?.updateLastMessageId(mapper.chatIdHex(chatId), messageId)
    }

    suspend fun updateLatestEventSequence(chatId: ChatId, sequence: Long) {
        db?.chatMetadataDao()?.updateLatestEventSequence(mapper.chatIdHex(chatId), sequence)
    }

    suspend fun getLatestEventSequence(chatId: ChatId): Long =
        db?.chatMetadataDao()?.getLatestEventSequence(mapper.chatIdHex(chatId)) ?: 0L

    suspend fun getChatType(chatId: ChatId): ChatType {
        val stored = db?.chatMetadataDao()?.getChatType(mapper.chatIdHex(chatId))
        return ChatType.entries.firstOrNull { it.name == stored } ?: ChatType.UNKNOWN
    }

    /** [chatId]'s group name, or null when the chat is a DM or is not stored on this device. */
    suspend fun getTitle(chatId: ChatId): String? =
        db?.chatMetadataDao()?.getTitle(mapper.chatIdHex(chatId))

    suspend fun getAnalyticsCountedThrough(chatId: ChatId): Long =
        db?.chatMetadataDao()?.getAnalyticsCountedThrough(mapper.chatIdHex(chatId)) ?: 0L

    suspend fun advanceAnalyticsCountedThrough(chatId: ChatId, messageId: Long) {
        db?.chatMetadataDao()?.advanceAnalyticsCountedThrough(mapper.chatIdHex(chatId), messageId)
    }

    suspend fun setHidden(chatId: ChatId, hidden: Boolean) {
        db?.chatMetadataDao()?.updateHidden(mapper.chatIdHex(chatId), hidden)
    }

    suspend fun exists(chatId: ChatId): Boolean =
        db?.chatMetadataDao()?.getById(mapper.chatIdHex(chatId)) != null

    fun toMetadata(
        entity: ChatMetadataEntity,
        members: List<ChatMember>,
        lastMessage: ChatMessage?,
    ): ChatMetadata = mapper.toMetadata(entity, members, lastMessage)

    suspend fun clear() {
        db?.chatMetadataDao()?.deleteAll()
    }

    private fun emptyPagingSource() = object : PagingSource<Int, ChatMetadataEntity>() {
        override fun getRefreshKey(state: androidx.paging.PagingState<Int, ChatMetadataEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatMetadataEntity> =
            LoadResult.Error(Exception("Database not initialized"))
    }
}
