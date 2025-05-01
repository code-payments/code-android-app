package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flipcash.app.persistence.dao.MessageDao
import com.flipcash.app.persistence.entities.MessageEntity
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.subByteArray

@Database(
    entities = [
        MessageEntity::class
    ],
    version = 1,
)
//@TypeConverters(Converters::class)
abstract class FlipcashDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        private var instance: FlipcashDatabase? = null
        fun requireInstance() = requireNotNull(instance)
        fun getInstance(): FlipcashDatabase? = instance
        private var dbName: String = ""

        private const val dbNamePrefix = "fcash_database"

        fun isOpen() = instance?.isOpen == true

        fun init(context: Context, entropyB64: String) {
            val dbUniqueName = Base58.encode(entropyB64.toByteArray().subByteArray(0, 6))
            trace("database init start $dbUniqueName", type = TraceType.Process)
            instance?.close()
            dbName = "$dbNamePrefix-$dbUniqueName.db"

            instance =
                Room.databaseBuilder(context, FlipcashDatabase::class.java, dbName)
                    .fallbackToDestructiveMigration()
                    .build()

            trace("database init end", type = TraceType.Process)
        }

        @Synchronized
        fun closeDb() {
            if (instance != null) {
                instance?.close()
                instance = null
            }
        }
    }
}