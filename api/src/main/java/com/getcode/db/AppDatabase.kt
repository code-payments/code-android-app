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
import com.getcode.network.repository.decodeBase64
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import net.sqlcipher.database.SupportFactory
import org.kin.sdk.base.tools.subByteArray
import timber.log.Timber
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
        Conversation::class,
        ConversationPointerCrossRef::class,
        ConversationMessage::class,
        ConversationIntentIdReference::class,
        ConversationMessageContent::class,
    ],
    autoMigrations = [
        AutoMigration(from = 7, to = 8, spec = AppDatabase.Migration7To8::class),
        AutoMigration(from = 8, to = 9, spec = AppDatabase.Migration8To9::class),
        AutoMigration(from = 10, to = 11, spec = AppDatabase.Migration10To11::class),
        AutoMigration(from = 11, to = 12, spec = AppDatabase.Migration11To12::class),
        AutoMigration(from = 12, to = 13, spec = AppDatabase.Migration12To13::class),
        AutoMigration(from = 13, to = 14, spec = AppDatabase.Migration13To14::class),
        AutoMigration(from = 14, to = 15, spec = AppDatabase.Migration14To15::class),
    ],
    version = 15
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prefIntDao(): PrefIntDao
    abstract fun prefStringDao(): PrefStringDao
    abstract fun prefBoolDao(): PrefBoolDao
    abstract fun prefDoubleDao(): PrefDoubleDao
    abstract fun giftCardDao(): GiftCardDao
    abstract fun exchangeDao(): ExchangeDao

    abstract fun conversationDao(): ConversationDao
    abstract fun conversationPointersDao(): ConversationPointerDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun conversationIntentMappingDao(): ConversationIntentMappingDao

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
    class Migration13To14: AutoMigrationSpec

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
    class Migration14To15: AutoMigrationSpec
}

object Database {
    private const val dbNamePrefix = "AppDatabase"
    private const val dbNameSuffix = ".db"
    private var instance: AppDatabase? = null
    private var dbName: String = ""
    private var isInitSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()
    var isInit = isInitSubject.toFlowable(BackpressureStrategy.DROP).filter { true }
    fun isOpen() = instance?.isOpen == true
    fun getInstance() = instance
    fun requireInstance() = instance!!

    fun init(context: Context, entropyB64: String) {
        trace("database init start", type = TraceType.Process)
        instance?.close()
        val dbUniqueName = Base58.encode(entropyB64.toByteArray().subByteArray(0, 3))
        dbName = "$dbNamePrefix-$dbUniqueName$dbNameSuffix"

        instance =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
//                .openHelperFactory(SupportFactory(entropyB64.decodeBase64(), null, false))
                .fallbackToDestructiveMigration()
                .build()

        isInitSubject.onNext(true)
        trace("database init end", type = TraceType.Process)
    }

    fun close() {
        Timber.d("close")
        instance?.close()
        instance = null
        isInitSubject.onNext(false)
    }

    fun delete(context: Context) {
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
}