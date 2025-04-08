package com.flipcash.services.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.subByteArray
import timber.log.Timber
import java.io.File

abstract class FlipcashDatabase : RoomDatabase() {

    companion object {
        private var instance: FlipcashDatabase? = null
        fun requireInstance() = requireNotNull(instance)
        fun getInstance(): FlipcashDatabase? = instance
        private var dbName: String = ""

        private const val dbNamePrefix = "flipcash"

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
                Timber.d("close")
                instance?.close()
                instance = null
            }
        }

        @Synchronized
        fun deleteDb(context: Context) {
            Timber.d("delete")
            closeDb()
            if (dbName.isEmpty()) return

            val databases = File(context.applicationInfo.dataDir + "/databases")
            val db = File(databases, dbName)
            db.delete()

            val journal = File(databases, "$dbName-journal")
            val shm = File(databases, "$dbName-shm")
            val wal = File(databases, "$dbName-wal")

            if (journal.exists()) journal.delete()
            if (shm.exists()) shm.delete()
            if (wal.exists()) shm.delete()
        }
    }
}