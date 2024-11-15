package xyz.flipchat.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefInt
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PrefIntDao {
    @Query("SELECT * FROM PrefInt WHERE key = :key")
    suspend fun get(key: String): PrefInt?

    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun observe(key: String): Flow<PrefInt?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefInt)
}