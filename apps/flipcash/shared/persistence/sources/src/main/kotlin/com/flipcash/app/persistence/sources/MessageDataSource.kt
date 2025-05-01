package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.sqlite.db.SimpleSQLiteQuery
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.MessageEntityToFeedMessageMapper
import com.flipcash.app.persistence.sources.mapper.NotificationToEntityMapper
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.persistence.PagingDataSource
import com.getcode.opencode.model.core.ID
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

    override fun observe(): PagingSource<Int, MessageEntity> {
        return db?.messageDao()?.observeMessages() ?: object : PagingSource<Int, MessageEntity>() {
            override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> =
                LoadResult.Error(Exception("Database not initialized"))
        }
    }
}