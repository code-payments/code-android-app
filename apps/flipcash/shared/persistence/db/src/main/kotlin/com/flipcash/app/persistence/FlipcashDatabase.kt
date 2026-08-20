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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.flipcash.app.persistence.converters.ChatTypeConverters
import com.flipcash.app.persistence.converters.TokenTypeConverters
import com.flipcash.app.persistence.dao.BlockedUserDao
import com.flipcash.app.persistence.dao.ChatMemberDao
import com.flipcash.app.persistence.dao.ChatMessageDao
import com.flipcash.app.persistence.dao.ChatMetadataDao
import com.flipcash.app.persistence.dao.ContactDao
import com.flipcash.app.persistence.dao.CurrencyCreatorDraftDao
import com.flipcash.app.persistence.dao.MessageDao
import com.flipcash.app.persistence.dao.TokenDao
import com.flipcash.app.persistence.dao.UserProfileDao
import com.flipcash.app.persistence.entities.BlockedUserEntity
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
import com.flipcash.app.persistence.entities.UserProfileEntity
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
        BlockedUserEntity::class,
        UserProfileEntity::class,
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
        AutoMigration(from = 24, to = 25),
        // 25 -> 26 is a manual migration (MIGRATION_25_26): it normalizes the
        // per-row user_profile_json blob into the shared user_profiles table, which
        // needs data movement an AutoMigration can't express.
        AutoMigration(from = 26, to = 27), // messages.text_substitutions (nullable)
        AutoMigration(from = 27, to = 28), // tokens.market_cap_metrics (nullable)
        AutoMigration(from = 28, to = 29, spec = FlipcashDatabase.Migration28To29::class),
    ],
    version = 29,
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
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun userProfileDao(): UserProfileDao

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

    /**
     * Data-only migration: swap ("Converted") notifications cached before multi-mint amounts were
     * mapped carry NULL financials — no amount, rate, or mint — because a swap leaves
     * `payment_amount` unset and puts its amounts in `additional_metadata`. Those rows can't be
     * repaired in place (the data was never persisted) and they can't be re-fetched either:
     * `ActivityFeedCoordinator.fetchSinceLatest` only ever walks *forward* from the newest cached
     * id, so anything already cached is never requested again.
     *
     * Clearing the cache is the repair. The next sync sees an empty table, falls into its
     * descending re-seed, and re-fetches the recent feed from scratch; paging refills older
     * history on demand.
     */
    class Migration28To29 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM messages")
        }
    }

    companion object {

        /**
         * Normalizes the profile cache. Before v26 the full profile was serialized as
         * `user_profile_json` and duplicated onto every `chat_members` row (one per
         * membership) and onto `blocked_users`. v26 collapses it into a single
         * `user_profiles` table keyed by `user_id_hex`, joined back via `@Relation`.
         *
         * The blob can't be decomposed into columns in SQL, so each user's blob is staged
         * verbatim into `user_profiles.pending_migration_json` (one row per user; the full
         * chat blob is preferred over the name+avatar-only blocked blob via `INSERT OR
         * IGNORE`). A one-shot Kotlin backfill ([backfillMigratedProfiles]) decomposes it
         * afterwards, and reads fall back to the staged blob until it runs.
         *
         * Column drops use table-recreate (not `ALTER TABLE ... DROP COLUMN`) for
         * compatibility with the minSdk-29 SQLite build.
         */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. New normalized table. Column set must match UserProfileEntity so
                //    Room's post-migration schema validation passes.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profiles` (" +
                        "`user_id_hex` TEXT NOT NULL, " +
                        "`display_name` TEXT NOT NULL, " +
                        "`phone_value` TEXT, " +
                        "`phone_verified` INTEGER, " +
                        "`email_value` TEXT, " +
                        "`email_verified` INTEGER, " +
                        "`social_accounts_json` TEXT, " +
                        "`profile_picture_json` TEXT, " +
                        "`pending_migration_json` TEXT, " +
                        "PRIMARY KEY(`user_id_hex`))"
                )

                // 2. Stage each user's legacy blob (one row per user). chat_members holds
                //    the full profile, so insert it first; blocked_users (name+avatar only)
                //    fills in users not in any chat. display_name is a placeholder until
                //    the backfill parses the blob.
                db.execSQL(
                    "INSERT OR IGNORE INTO user_profiles (user_id_hex, display_name, pending_migration_json) " +
                        "SELECT user_id_hex, '', user_profile_json FROM chat_members " +
                        "WHERE user_profile_json IS NOT NULL"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO user_profiles (user_id_hex, display_name, pending_migration_json) " +
                        "SELECT user_id_hex, '', user_profile_json FROM blocked_users " +
                        "WHERE user_profile_json IS NOT NULL"
                )

                // 3. Drop the duplicated blob column from chat_members via table-recreate.
                db.execSQL(
                    "CREATE TABLE `chat_members_new` (" +
                        "`chat_id_hex` TEXT NOT NULL, " +
                        "`user_id_hex` TEXT NOT NULL, " +
                        "`pointers_json` TEXT, " +
                        "PRIMARY KEY(`chat_id_hex`, `user_id_hex`))"
                )
                db.execSQL(
                    "INSERT INTO `chat_members_new` (chat_id_hex, user_id_hex, pointers_json) " +
                        "SELECT chat_id_hex, user_id_hex, pointers_json FROM chat_members"
                )
                db.execSQL("DROP TABLE chat_members")
                db.execSQL("ALTER TABLE chat_members_new RENAME TO chat_members")

                // 4. Same for blocked_users.
                db.execSQL(
                    "CREATE TABLE `blocked_users_new` (" +
                        "`user_id_hex` TEXT NOT NULL, " +
                        "`blocked_at_epoch_ms` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`user_id_hex`))"
                )
                db.execSQL(
                    "INSERT INTO `blocked_users_new` (user_id_hex, blocked_at_epoch_ms) " +
                        "SELECT user_id_hex, blocked_at_epoch_ms FROM blocked_users"
                )
                db.execSQL("DROP TABLE blocked_users")
                db.execSQL("ALTER TABLE blocked_users_new RENAME TO blocked_users")
            }
        }

        // Reactive mirror of [instance] so consumers can observe the DB becoming available/torn down
        // (the per-user DB is created on login, after singletons build their flow graphs) instead of
        // polling. Kept in lockstep with [instance] in init()/closeDb().
        private val instanceState = MutableStateFlow<FlipcashDatabase?>(null)
        fun observeInstance(): StateFlow<FlipcashDatabase?> = instanceState.asStateFlow()

        private var instance: FlipcashDatabase? = null
            set(value) {
                field = value
                instanceState.value = value
            }
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
                    .addMigrations(MIGRATION_25_26)
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