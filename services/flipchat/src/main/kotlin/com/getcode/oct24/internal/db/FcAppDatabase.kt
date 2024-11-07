package com.getcode.oct24.internal.db

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
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.chat.ConversationMember
import com.getcode.oct24.domain.model.chat.ConversationMessage
import com.getcode.oct24.domain.model.chat.ConversationMessageContent
import com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef
import com.getcode.services.db.ClosableDatabase
import com.getcode.services.db.SharedConverters
import com.getcode.services.model.PrefBool
import com.getcode.services.model.PrefDouble
import com.getcode.services.model.PrefInt
import com.getcode.services.model.PrefString
import com.getcode.utils.TraceType
import com.getcode.utils.decodeBase64
import com.getcode.utils.trace
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import net.sqlcipher.database.SupportFactory
import org.kin.sdk.base.tools.subByteArray
import java.io.File

@Database(
    entities = [
        PrefInt::class,
        PrefString::class,
        PrefBool::class,
        PrefDouble::class,
        Conversation::class,
        ConversationMember::class,
        ConversationPointerCrossRef::class,
        ConversationMessage::class,
        ConversationMessageContent::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = FcAppDatabase.Migration6To7::class),
    ],
    version = 7,
)
@TypeConverters(SharedConverters::class, Converters::class)
internal abstract class FcAppDatabase : RoomDatabase(), ClosableDatabase {
    abstract fun prefIntDao(): PrefIntDao
    abstract fun prefStringDao(): PrefStringDao
    abstract fun prefBoolDao(): PrefBoolDao
    abstract fun prefDoubleDao(): PrefDoubleDao

    abstract fun conversationDao(): ConversationDao
    abstract fun conversationPointersDao(): ConversationPointerDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun conversationMembersDao(): ConversationMemberDao

    class Migration6To7 : Migration(6, 7), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM members")
        }
    }

    override fun closeDb() {
        instance?.close()
        instance = null
        isInitSubject.onNext(false)
    }

    override fun deleteDb(context: Context) {
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
        private var instance: FcAppDatabase? = null
        private var isInitSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()
        var isInit = isInitSubject.toFlowable(BackpressureStrategy.DROP).filter { true }
        fun isOpen() = instance?.isOpen == true
        fun getInstance() = instance
        fun requireInstance() = instance!!
        private var dbName: String = ""

        private const val dbNamePrefix = "FcAppDatabase"
        private const val dbNameSuffix = ".db"

        fun init(context: Context, entropyB64: String) {
            trace("database init start", type = TraceType.Process)
            instance?.close()
            val dbUniqueName =
                com.getcode.vendor.Base58.encode(entropyB64.toByteArray().subByteArray(0, 3))
            dbName = "$dbNamePrefix-$dbUniqueName$dbNameSuffix"

            instance =
                Room.databaseBuilder(context, FcAppDatabase::class.java, dbName)
                    .openHelperFactory(SupportFactory(entropyB64.decodeBase64(), null, false))
                    .fallbackToDestructiveMigration()
                    .build()

            isInitSubject.onNext(true)
            trace("database init end", type = TraceType.Process)
        }
    }
}