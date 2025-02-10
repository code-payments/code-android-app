package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ID
import com.getcode.utils.base58
import xyz.flipchat.services.domain.model.people.FlipchatUser

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
}