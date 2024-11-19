package com.getcode.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getcode.model.*
import com.getcode.services.db.ClosableDatabase
import com.getcode.services.db.SharedConverters
import com.getcode.services.model.PrefBool
import com.getcode.services.model.PrefDouble
import com.getcode.services.model.PrefInt
import com.getcode.services.model.PrefString
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.kin.sdk.base.tools.subByteArray
import java.io.File

@Database(
    entities = [
        CurrencyRate::class,
        FaqItem::class,
        PrefInt::class,
        PrefString::class,
        PrefBool::class,
        PrefDouble::class,
        GiftCard::class,
        ExchangeRate::class,
    ],
    autoMigrations = [
        AutoMigration(from = 7, to = 8, spec = CodeAppDatabase.Migration7To8::class),
        AutoMigration(from = 8, to = 9, spec = CodeAppDatabase.Migration8To9::class),
        AutoMigration(from = 10, to = 11, spec = CodeAppDatabase.Migration10To11::class),
        AutoMigration(from = 11, to = 12, spec = CodeAppDatabase.Migration11To12::class),
        AutoMigration(from = 12, to = 13, spec = CodeAppDatabase.Migration12To13::class),
        AutoMigration(from = 13, to = 14, spec = CodeAppDatabase.Migration13To14::class),
        AutoMigration(from = 14, to = 15, spec = CodeAppDatabase.Migration14To15::class),
        AutoMigration(from = 15, to = 16, spec = CodeAppDatabase.Migration15To16::class),
    ],
    version = 16
)
@TypeConverters(SharedConverters::class)
abstract class CodeAppDatabase : RoomDatabase(), ClosableDatabase {
    abstract fun prefIntDao(): RxPrefIntDao
    abstract fun prefStringDao(): RxPrefStringDao
    abstract fun prefBoolDao(): RxPrefBoolDao
    abstract fun prefDoubleDao(): RxPrefDoubleDao
    abstract fun giftCardDao(): GiftCardDao
    abstract fun exchangeDao(): ExchangeDao

    @DeleteTable(tableName = "HistoricalTransaction")
    class Migration7To8 : AutoMigrationSpec

    @DeleteTable(tableName = "SendLimit")
    class Migration8To9 : AutoMigrationSpec

    class Migration10To11 : Migration(10, 11), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE messages")
        }
    }

    @RenameColumn.Entries(
        RenameColumn(
            tableName = "conversations",
            fromColumnName = "messageIdBase58",
            toColumnName = "idBase58"
        )
    )
    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "conversations",
            columnName = "cursorBase58"
        ),
        DeleteColumn(
            tableName = "conversations",
            columnName = "tipAmount"
        ),
        DeleteColumn(
            tableName = "conversations",
            columnName = "createdByUser"
        )
    )
    class Migration11To12 : Migration(11, 12), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE messages")
        }
    }

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "messages",
            columnName = "content"
        )
    )
    @DeleteTable("messages_remote_keys")
    class Migration12To13 : Migration(12, 13), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE messages")
        }
    }

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "messages",
            columnName = "status"
        )
    )
    class Migration13To14 : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "conversations",
            columnName = "user"
        ),
        DeleteColumn(
            tableName = "conversations",
            columnName = "userImage"
        )
    )
    class Migration14To15 : AutoMigrationSpec

    /**
     * Chat removed from Code and moved to Flipchat
     */
    @DeleteTable("conversations")
    @DeleteTable("conversation_pointers")
    @DeleteTable("messages")
    @DeleteTable("conversation_intent_id_mapping")
    @DeleteTable("message_contents")
    class Migration15To16 : AutoMigrationSpec

    override fun closeDb() {
        instance?.close()
        instance = null
        isInitSubject.onNext(false)
    }

    override suspend fun deleteDb(context: Context) {
        instance?.close()
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
        isInitSubject.onNext(false)
    }

    companion object {
        private var instance: CodeAppDatabase? = null
        private var isInitSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()
        var isInit = isInitSubject.toFlowable(BackpressureStrategy.DROP).filter { true }
        fun isOpen() = instance?.isOpen == true
        fun getInstance() = instance
        fun requireInstance() = instance!!
        private var dbName: String = ""

        private const val dbNamePrefix = "AppDatabase"
        private const val dbNameSuffix = ".db"

        fun init(context: Context, entropyB64: String) {
            trace("database init start", type = TraceType.Process)
            instance?.close()
            val dbUniqueName =
                com.getcode.vendor.Base58.encode(entropyB64.toByteArray().subByteArray(0, 3))
            dbName = "$dbNamePrefix-$dbUniqueName$dbNameSuffix"

            instance =
                Room.databaseBuilder(context, CodeAppDatabase::class.java, dbName)
//                .openHelperFactory(SupportFactory(entropyB64.decodeBase64(), null, false))
                    .fallbackToDestructiveMigration()
                    .build()

            isInitSubject.onNext(true)
            trace("database init end", type = TraceType.Process)
        }
    }
}