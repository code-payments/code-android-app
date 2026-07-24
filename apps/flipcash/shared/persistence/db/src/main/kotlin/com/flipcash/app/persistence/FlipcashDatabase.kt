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
import com.flipcash.app.persistence.converters.ChatTypeConverters
import com.flipcash.app.persistence.converters.TokenTypeConverters
import com.flipcash.app.persistence.dao.ChatMemberDao
import com.flipcash.app.persistence.dao.ChatMessageDao
import com.flipcash.app.persistence.dao.ChatMetadataDao
import com.flipcash.app.persistence.dao.ContactDao
import com.flipcash.app.persistence.dao.CurrencyCreatorDraftDao
import com.flipcash.app.persistence.dao.MessageDao
import com.flipcash.app.persistence.dao.TokenDao
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.ChatMetadataEntity
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
        ChatMetadataEntity::class,
        ChatMessageEntity::class,
        ChatMemberEntity::class,
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
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23, spec = FlipcashDatabase.Migration22To23::class),
        AutoMigration(from = 23, to = 24),
    ],
    version = 24,
)
@TypeConverters(TokenTypeConverters::class, ChatTypeConverters::class)
abstract class FlipcashDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun tokenDao(): TokenDao
    abstract fun currencyCreatorDraftDao(): CurrencyCreatorDraftDao
    abstract fun contactDao(): ContactDao
    abstract fun chatMetadataDao(): ChatMetadataDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatMemberDao(): ChatMemberDao

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

    // Data-only migration: the chat.v1.ChatType proto enum renamed DM -> CONTACT_DM
    // (and added TIP_DM), and chat_type is persisted as the domain enum's name. There
    // is no schema change, so this rides the auto migration and rewrites existing rows
    // in onPostMigrate so they keep resolving after the rename instead of falling
    // through to UNKNOWN.
    class Migration22To23 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE chat_metadata SET chat_type = 'CONTACT_DM' WHERE chat_type = 'DM'")
        }
    }

    companion object {
        private var instance: FlipcashDatabase? = null
        fun requireInstance() = requireNotNull(instance)
        fun getInstance(): FlipcashDatabase? = instance
        private var dbName: String = ""

        private const val dbNamePrefix = "fcash_database"

        fun isOpen() = instance?.isOpen == true

        @Synchronized
        fun init(context: Context, entropyB64: String) {
            val dbUniqueName = Base58.encode(entropyB64.toByteArray().subByteArray(0, 6))
            val targetName = "$dbNamePrefix-$dbUniqueName.db"

            // Same-user DB already built: don't tear down the existing instance.
            // A concurrent or soft re-login (e.g. app startup racing an FCM push
            // that also calls AuthManager.init) must not close the DB out from
            // under in-flight Room queries such as CashScreenViewModel's balance
            // Flow — doing so throws "connection pool has been closed". Key on the
            // DB name, not isOpen(): Room opens the file lazily, so the instance
            // can be live-but-not-yet-open in exactly this racy window.
            // closeDb() nulls `instance`, so a non-null instance here is current.
            if (instance != null && dbName == targetName) return

            trace("database init start $dbUniqueName", type = TraceType.Process)
            instance?.close()
            dbName = targetName

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