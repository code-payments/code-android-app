package xyz.flipchat.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefString
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefStringDao {
    @Query("SELECT * FROM PrefString WHERE key = :key")
    suspend fun get(key: String): PrefString?

    @Query("SELECT * FROM PrefString WHERE key = :key")
    fun observe(key: String): Flow<PrefString?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefString)

    @Query("DELETE FROM PrefString")
    suspend fun clear()
}