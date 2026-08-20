package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SimpleSQLiteQuery
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.notifications.MessageEntityToFeedMessageMapper
import com.flipcash.app.persistence.sources.mapper.notifications.NotificationToEntityMapper
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.persistence.PagingDataSource
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDataSource @Inject constructor(
    private val messageEntityMapper: MessageEntityToFeedMessageMapper,
    private val notificationEntityMapper: NotificationToEntityMapper
): PagingDataSource<ID, ActivityFeedMessage, List<ActivityFeedNotification>, Int, MessageEntity> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    override suspend fun getById(id: ID): ActivityFeedMessage? {
        val result = db?.messageDao()?.getMessageById(id) ?: return null
        return messageEntityMapper.map(result)
    }

    override suspend fun get(): List<ActivityFeedMessage> {
        val result = db?.messageDao()?.getAllMessages() ?: return emptyList()
        return result.map { messageEntityMapper.map(it) }
    }

    override suspend fun query(whereClause: String): List<ActivityFeedMessage> {
        val query = SimpleSQLiteQuery("SELECT * FROM messages WHERE $whereClause")
        val result = db?.messageDao()?.queryDirectly(query) ?: return emptyList()
        return result.map { messageEntityMapper.map(it) }
    }

    override suspend fun getMostRecent(): ActivityFeedMessage? {
        val entity = db?.messageDao()?.getNewestMessage() ?: return null
        return messageEntityMapper.map(entity)
    }

    override suspend fun clear() {
        db?.messageDao()?.deleteAllMessages()
    }

    override suspend fun upsert(value: List<ActivityFeedNotification>) {
        val entities = notificationEntityMapper.map(value)
        db?.messageDao()?.upsert(*entities.toTypedArray())
    }

    /**
     * Reactive "has money ever come in" — any completed incoming notification (buy, deposit, or a
     * tip received).
     *
     * Resolved through [FlipcashDatabase.observeInstance] for the same reason as [observeRecent]:
     * the per-user DB is created at login, *after* singletons have built their flow graphs. Reading
     * `db` once at subscription time latched any subscriber that started before the DB existed onto
     * a constant `false` for the rest of the session — which pinned the wallet's onboarding
     * checklist to "incomplete" (and so kept the new-user tutorial on screen) no matter how much
     * activity later landed in the feed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun hasEverReceivedMoney(): Flow<Boolean> =
        FlipcashDatabase.observeInstance().flatMapLatest { database ->
            database?.messageDao()?.hasEverReceivedMoney() ?: flowOf(false)
        }

    /**
     * Observes the [limit] most recent messages (newest first) as domain models. Reacts to the
     * per-user DB becoming available (created on login) via [FlipcashDatabase.observeInstance] rather
     * than capturing a possibly-null instance once, so it emits as soon as the DB is ready.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecent(limit: Int): Flow<List<ActivityFeedMessage>> =
        FlipcashDatabase.observeInstance().flatMapLatest { database ->
            database?.messageDao()?.observeRecent(limit)?.map { entities ->
                entities.map { messageEntityMapper.map(it) }
            } ?: flowOf(emptyList())
        }

    /**
     * Observes the [limit] most recent messages for a single token (newest first) as domain models —
     * the token info screen's per-token activity preview. Same DB-readiness handling as [observeRecent].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecent(mint: Mint, limit: Int): Flow<List<ActivityFeedMessage>> =
        FlipcashDatabase.observeInstance().flatMapLatest { database ->
            database?.messageDao()?.observeRecentForMint(mint.base58(), limit)?.map { entities ->
                entities.map { messageEntityMapper.map(it) }
            } ?: flowOf(emptyList())
        }

    override fun observe(): PagingSource<Int, MessageEntity> {
        return db?.messageDao()?.observeMessages() ?: object : PagingSource<Int, MessageEntity>() {
            override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> =
                LoadResult.Error(Exception("Database not initialized"))
        }
    }
}