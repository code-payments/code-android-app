package xyz.flipchat.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefBool
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PrefBoolDao {
    @Query("SELECT * FROM PrefBool WHERE key = :key")
    suspend fun get(key: String): PrefBool?
    @Query("SELECT * FROM PrefBool WHERE key = :key")
    fun observe(key: String): Flow<PrefBool?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefBool)
}