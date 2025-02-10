package xyz.flipchat.internal.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getcode.services.db.ClosableDatabase
import com.getcode.services.db.SharedConverters
import com.getcode.services.model.PrefBool
import com.getcode.services.model.PrefDouble
import com.getcode.services.model.PrefInt
import com.getcode.services.model.PrefString
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.subByteArray
import timber.log.Timber
import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationMessageTip
import xyz.flipchat.services.domain.model.chat.ConversationPointerCrossRef
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.internal.db.ConversationDao
import xyz.flipchat.services.internal.db.ConversationMemberDao
import xyz.flipchat.services.internal.db.ConversationMessageDao
import xyz.flipchat.services.internal.db.ConversationPointerDao
import xyz.flipchat.services.internal.db.UserDao
import java.io.File

@Database(
    entities = [
        PrefInt::class,
        PrefString::class,
        PrefBool::class,
        PrefDouble::class,
        FlipchatUser::class,
        Conversation::class,
        ConversationMember::class,
        ConversationPointerCrossRef::class,
        ConversationMessage::class,
        ConversationMessageTip::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = FcAppDatabase.Migration6To7::class),
        AutoMigration(from = 7, to = 8, spec = FcAppDatabase.Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, spec = FcAppDatabase.Migration9To10::class),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12, spec = FcAppDatabase.Migration11To12::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14, spec = FcAppDatabase.Migration13To14::class),
        AutoMigration(from = 14, to = 15, spec = FcAppDatabase.Migration14To15::class),
        AutoMigration(from = 15, to = 16),
        // explicit no migration to fallback to reset (from = 16, to = 17)
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21, spec = FcAppDatabase.Migration20To21::class),
        AutoMigration(from = 21, to = 22, spec = FcAppDatabase.Migration21To22::class),
        // explicit no migration to fallback to reset (from = 22, to = 23)
    ],
    version = 23,
)
@TypeConverters(SharedConverters::class, Converters::class)
abstract class FcAppDatabase : RoomDatabase(), ClosableDatabase {
    abstract fun prefIntDao(): PrefIntDao
    abstract fun prefStringDao(): PrefStringDao
    abstract fun prefBoolDao(): PrefBoolDao
    abstract fun prefDoubleDao(): PrefDoubleDao

    abstract fun conversationDao(): ConversationDao
    abstract fun conversationPointersDao(): ConversationPointerDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun conversationMembersDao(): ConversationMemberDao
    abstract fun userDao(): UserDao

    class Migration6To7 : Migration(6, 7), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM members")
        }
    }

    class Migration7To8 : Migration(7, 8), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM conversations")
        }
    }

    class Migration9To10 : Migration(9, 10), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            // drop messages to allow proper mapping for announcements
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration11To12 : Migration(11, 12), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add indexes for messages
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationIdBase58 ON messages(conversationIdBase58)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_senderIdBase58 ON messages(senderIdBase58)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_dateMillis ON messages(dateMillis)")

            // Add index for message_contents
            db.execSQL("CREATE INDEX IF NOT EXISTS index_message_contents_messageIdBase58 ON message_contents(messageIdBase58)")

            // Add indexes for members
            db.execSQL("CREATE INDEX IF NOT EXISTS index_members_memberIdBase58 ON members(memberIdBase58)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_members_conversationIdBase58 ON members(conversationIdBase58)")
        }
    }

    class Migration13To14 : Migration(13, 14), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE members ADD COLUMN isFullMember INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE members SET isFullMember = 1")
        }
    }

    class Migration14To15 : Migration(14, 15), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            // drop messages to allow proper use of message ID as the timestamp
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration20To21 : Migration(20, 21), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    class Migration21To22 : Migration(21, 22), AutoMigrationSpec {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
            db.execSQL("DELETE FROM members")
        }
    }

    @Synchronized
    override fun closeDb() {
        if (instance != null) {
            Timber.d("close")
            instance?.close()
            instance = null
        }
    }

    @Synchronized
    override fun deleteDb(context: Context) {
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

    companion object {
        private var instance: FcAppDatabase? = null
        fun requireInstance() = requireNotNull(instance)
        fun getInstance(): FcAppDatabase? = instance
        private var dbName: String = ""

        private const val dbNamePrefix = "FcAppDatabase"

        fun isOpen() = instance?.isOpen == true

        fun init(context: Context, entropyB64: String) {
            val dbUniqueName = Base58.encode(entropyB64.toByteArray().subByteArray(0, 6))
            trace("database init start $dbUniqueName", type = TraceType.Process)
            instance?.close()
            dbName = "$dbNamePrefix-$dbUniqueName.db"

            instance =
                Room.databaseBuilder(context, FcAppDatabase::class.java, dbName)
                    .fallbackToDestructiveMigration()
                    .build()

            trace("database init end", type = TraceType.Process)
        }
    }
}