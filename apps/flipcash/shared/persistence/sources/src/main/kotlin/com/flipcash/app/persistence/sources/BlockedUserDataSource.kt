package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.withTransaction
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.BlockedUserWithProfile
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
    fun observe(): PagingSource<Int, BlockedUserWithProfile> {
        return db?.blockedUserDao()?.observePaged() ?: object : PagingSource<Int, BlockedUserWithProfile>() {
            override fun getRefreshKey(state: PagingState<Int, BlockedUserWithProfile>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BlockedUserWithProfile> =
                LoadResult.Error(IllegalStateException("Database not initialized"))
        }
    }

    fun toProfile(entity: BlockedUserWithProfile): BlockedUserProfile = toProfileMapper.map(entity)

    suspend fun upsert(resolved: List<ResolvedBlockedUser>) {
        val database = db ?: return
        database.withTransaction {
            database.blockedUserDao().upsert(resolved.map { toEntityMapper.map(it) })
            resolved.forEach { database.writeProfile(it) }
        }
    }

    /** Atomically replaces the cached blocklist — a single Room invalidation, no empty flicker. */
    suspend fun replaceAll(resolved: List<ResolvedBlockedUser>) {
        val database = db ?: return
        database.withTransaction {
            database.blockedUserDao().replaceAll(resolved.map { toEntityMapper.map(it) })
            resolved.forEach { database.writeProfile(it) }
        }
    }

    suspend fun clear() {
        db?.blockedUserDao()?.deleteAll()
    }

    suspend fun delete(userId: ID) {
        db?.blockedUserDao()?.delete(userId.hexEncodedString())
    }

    /**
     * Writes the blocked user's name + avatar into the shared `user_profiles` table without
     * disturbing any richer profile already cached from a chat (the blocklist only resolves
     * those two fields). See [com.flipcash.app.persistence.dao.UserProfileDao.upsertNameAndAvatar].
     */
    private suspend fun FlipcashDatabase.writeProfile(resolved: ResolvedBlockedUser) {
        userProfileDao().upsertNameAndAvatar(
            userIdHex = resolved.blocked.userId.hexEncodedString(),
            displayName = resolved.profile?.displayName.orEmpty(),
            profilePicture = resolved.profile?.profilePicture,
        )
    }
}
