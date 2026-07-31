package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.BlockedUserEntity
import com.flipcash.app.persistence.sources.mapper.blocklist.BlockedUserEntityToProfileMapper
import com.flipcash.app.persistence.sources.mapper.blocklist.BlockedUserToEntityMapper
import com.flipcash.app.persistence.sources.mapper.blocklist.ResolvedBlockedUser
import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedUserDataSource @Inject constructor(
    private val toEntityMapper: BlockedUserToEntityMapper,
    private val toProfileMapper: BlockedUserEntityToProfileMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    /** Room-backed paging source for the cached blocklist; empty until the DB is initialized. */
    fun observe(): PagingSource<Int, BlockedUserEntity> {
        return db?.blockedUserDao()?.observePaged() ?: object : PagingSource<Int, BlockedUserEntity>() {
            override fun getRefreshKey(state: PagingState<Int, BlockedUserEntity>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BlockedUserEntity> =
                LoadResult.Error(IllegalStateException("Database not initialized"))
        }
    }

    fun toProfile(entity: BlockedUserEntity): BlockedUserProfile = toProfileMapper.map(entity)

    suspend fun upsert(resolved: List<ResolvedBlockedUser>) {
        db?.blockedUserDao()?.upsert(resolved.map { toEntityMapper.map(it) })
    }

    /** Atomically replaces the cached blocklist — a single Room invalidation, no empty flicker. */
    suspend fun replaceAll(resolved: List<ResolvedBlockedUser>) {
        db?.blockedUserDao()?.replaceAll(resolved.map { toEntityMapper.map(it) })
    }

    suspend fun clear() {
        db?.blockedUserDao()?.deleteAll()
    }

    suspend fun delete(userId: ID) {
        db?.blockedUserDao()?.delete(userId.hexEncodedString())
    }
}
