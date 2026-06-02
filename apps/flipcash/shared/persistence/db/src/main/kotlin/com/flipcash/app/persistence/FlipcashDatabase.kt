package com.flipcash.app.persistence

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flipcash.app.persistence.converters.TokenTypeConverters
import com.flipcash.app.persistence.dao.ContactDao
import com.flipcash.app.persistence.dao.CurrencyCreatorDraftDao
import com.flipcash.app.persistence.dao.MessageDao
import com.flipcash.app.persistence.dao.TokenDao
import com.flipcash.app.persistence.entities.ContactMappingEntity
import com.flipcash.app.persistence.entities.ContactSyncStateEntity
import com.flipcash.app.persistence.entities.CurrencyCreatorDraftEntity
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.persistence.entities.SocialLinkEntity
import com.flipcash.app.persistence.entities.TokenEntity
import com.flipcash.app.persistence.entities.TokenValuationEntity
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.getcode.utils.subByteArray

@Database(
    entities = [
        MessageEntity::class,
        TokenEntity::class,
        SocialLinkEntity::class,
        TokenValuationEntity::class,
        CurrencyCreatorDraftEntity::class,
        ContactSyncStateEntity::class,
        ContactMappingEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = FlipcashDatabase.Migration1To2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = FlipcashDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5, spec = FlipcashDatabase.Migration4To5::class),
        AutoMigration(from = 5, to = 6, spec = FlipcashDatabase.Migration5To6::class),
        AutoMigration(from = 6, to = 7, spec = FlipcashDatabase.Migration6To7::class),
        AutoMigration(from = 7, to = 8, spec = FlipcashDatabase.Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, spec = FlipcashDatabase.Migration9To10::class),
        AutoMigration(from = 10, to = 11, spec = FlipcashDatabase.Migration10To11::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, spec = FlipcashDatabase.Migration12To13::class),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
    ],
    version = 18,
)
@TypeConverters(TokenTypeConverters::class)
abstract class FlipcashDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun tokenDao(): TokenDao
    abstract fun currencyCreatorDraftDao(): CurrencyCreatorDraftDao
    abstract fun contactDao(): ContactDao

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
        ),
        DeleteColumn(
            tableName = "pool_metadata",
            columnName = "didWin"
        ),
    )
    class Migration5To6 : Migration(5, 6), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM pool_metadata")
            db.execSQL("DELETE FROM pool_bet_metadata")
        }
    }

    class Migration6To7 : Migration(6, 7), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration7To8 : Migration(7, 8), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration9To10 : Migration(9, 10), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    @DeleteTable.Entries(
        DeleteTable(tableName = "pool_metadata"),
        DeleteTable(tableName = "pool_bet_metadata"),
        DeleteTable(tableName = "pool_rendezvous_keys")
    )
    class Migration10To11 : Migration(10, 11), AutoMigrationSpec

    class Migration12To13 : Migration(12, 13), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM tokens")
            db.execSQL("DELETE FROM token_valuation")
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