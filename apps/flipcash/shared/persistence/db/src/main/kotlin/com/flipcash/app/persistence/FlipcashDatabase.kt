package com.flipcash.app.persistence

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.app.persistence.dao.MessageDao
import com.flipcash.app.persistence.dao.PoolDao
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.app.persistence.entities.PoolEntity
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.subByteArray

@Database(
    entities = [
        MessageEntity::class,
        PoolEntity::class,
        PoolBetEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = FlipcashDatabase.Migration1To2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = FlipcashDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5, spec = FlipcashDatabase.Migration4To5::class),
        AutoMigration(from = 5, to = 6, spec = FlipcashDatabase.Migration5To6::class),
    ],
    version = 6,
)
@TypeConverters(PoolResolutionConverter::class, BetOutcomeConverter::class)
abstract class FlipcashDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun poolDao(): PoolDao

    class Migration1To2 : Migration(1, 2), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration3To4 : Migration(3, 4), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM pool_metadata")
            db.execSQL("DELETE FROM pool_bet_metadata")
        }
    }

    class Migration4To5 : Migration(4, 5), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM pool_metadata")
            db.execSQL("DELETE FROM pool_bet_metadata")
        }
    }

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "pool_metadata",
            columnName = "rendezvousSeed"
        ),
        DeleteColumn(
            tableName = "pool_metadata",
            columnName = "didBet"
        )
    )
    class Migration5To6 : Migration(5, 6), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM pool_metadata")
            db.execSQL("DELETE FROM pool_bet_metadata")
        }
    }

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