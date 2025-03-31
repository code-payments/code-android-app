package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ID
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.flipchat.services.data.MemberIdentity
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.domain.model.people.FlipchatUserWithSocialProfiles

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg users: FlipchatUser)

    @Query("UPDATE users SET isBlocked = 1 WHERE userIdBase58 = :userId")
    suspend fun blockUser(userId: String)
    suspend fun blockUser(userId: ID) {
        blockUser(userId.base58)
    }

    @Query("UPDATE users SET isBlocked = 0 WHERE userIdBase58 = :userId")
    suspend fun unblockUser(userId: String)
    suspend fun unblockUser(userId: ID) {
        unblockUser(userId.base58)
    }

    @Query("UPDATE users SET memberName = :displayName, imageUri = :profileImageUrl WHERE userIdBase58 = :memberId")
    suspend fun updateIdentity(memberId: String, displayName: String, profileImageUrl: String?)

    suspend fun updateIdentity(memberId: ID, identity: MemberIdentity) {
        updateIdentity(memberId.base58, identity.displayName, identity.imageUrl)
    }

    @Query("SELECT * FROM users WHERE userIdBase58 IN (:userIds)")
    fun getUsersFrom(userIds: List<String>): Flow<List<FlipchatUserWithSocialProfiles>>
    fun getUsersFromIds(userIds: List<ID>): Flow<List<FlipchatUserWithSocialProfiles>> {
        return getUsersFrom(userIds.map { it.base58 })
            .map { users ->
                users.sortedBy { userIds.indexOf(it.user.id) }
            }
    }
}