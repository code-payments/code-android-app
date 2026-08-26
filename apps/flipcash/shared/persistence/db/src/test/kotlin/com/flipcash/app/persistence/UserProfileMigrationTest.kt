package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.converters.ChatTypeConverters
import com.flipcash.app.persistence.converters.SocialAccountSerialized
import com.flipcash.app.persistence.converters.UserProfileSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.UserProfileEntity
import com.flipcash.app.persistence.entities.decomposePending
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.MediaItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class UserProfileMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val converters = ChatTypeConverters()

    private val fullProfileJson = converters.toUserProfile(
        UserProfileSerialized(
            displayName = "Alice",
            socialAccounts = listOf(
                SocialAccountSerialized.TwitterX(
                    id = "1", username = "alice", name = "Alice",
                    description = "d", profilePicUrl = "u", verifiedType = "BLUE", followerCount = 5,
                )
            ),
            phoneNumber = VerifiableContactMethod("+15551234567", verified = true),
            email = VerifiableContactMethod("a@b.com", verified = false),
            profilePicture = MediaItem(renditions = emptyList()),
        )
    )!!

    private val partialProfileJson = converters.toUserProfile(
        UserProfileSerialized(
            displayName = "Bob",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            profilePicture = null,
        )
    )!!

    private fun openV25Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(callback)
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        // The v25 shape: profile duplicated as a JSON blob on both tables.
        db.execSQL(
            "CREATE TABLE chat_members (chat_id_hex TEXT NOT NULL, user_id_hex TEXT NOT NULL, " +
                "user_profile_json TEXT, pointers_json TEXT, PRIMARY KEY(chat_id_hex, user_id_hex))"
        )
        db.execSQL(
            "CREATE TABLE blocked_users (user_id_hex TEXT NOT NULL, blocked_at_epoch_ms INTEGER NOT NULL, " +
                "user_profile_json TEXT, PRIMARY KEY(user_id_hex))"
        )
        return db
    }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> {
        query("PRAGMA table_info($table)").use { c ->
            val names = mutableSetOf<String>()
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) names += c.getString(nameIdx)
            return names
        }
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.pendingFor(userId: String): String? =
        query("SELECT pending_migration_json FROM user_profiles WHERE user_id_hex = ?", arrayOf(userId))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    @Test
    fun `migration 25 to 26 normalizes duplicated blobs into one profile row per user`() {
        val db = openV25Database()
        // Same user's profile duplicated across two chats — the core redundancy this fixes.
        db.execSQL(
            "INSERT INTO chat_members VALUES (?,?,?,?)",
            arrayOf<Any?>("chatA", "u1", fullProfileJson, null),
        )
        db.execSQL(
            "INSERT INTO chat_members VALUES (?,?,?,?)",
            arrayOf<Any?>("chatB", "u1", fullProfileJson, null),
        )
        // A blocked user not in any chat, carrying only a partial (name+avatar) blob.
        db.execSQL(
            "INSERT INTO blocked_users VALUES (?,?,?)",
            arrayOf<Any?>("u2", 111L, partialProfileJson),
        )

        FlipcashDatabase.MIGRATION_25_26.migrate(db)

        // One profile row per user (u1 deduped across its two memberships).
        assertEquals(2, db.count("user_profiles"))
        assertEquals(fullProfileJson, db.pendingFor("u1"))
        assertEquals(partialProfileJson, db.pendingFor("u2"))

        // The duplicated blob column is gone from both tables; the rows themselves survive.
        assertFalse("user_profile_json" in db.columns("chat_members"))
        assertFalse("user_profile_json" in db.columns("blocked_users"))
        assertEquals(setOf("chat_id_hex", "user_id_hex", "pointers_json"), db.columns("chat_members"))
        assertEquals(2, db.count("chat_members"))
        assertEquals(1, db.count("blocked_users"))

        db.close()
    }

    @Test
    fun `decomposePending parses a staged blob into the normalized columns`() {
        val staged = UserProfileEntity(
            userIdHex = "u1",
            displayName = "",
            phoneValue = null, phoneVerified = null,
            emailValue = null, emailVerified = null,
            socialAccounts = null, profilePicture = null,
            pendingMigrationJson = fullProfileJson,
        )

        val decomposed = staged.decomposePending()

        assertEquals("Alice", decomposed.displayName)
        assertEquals("+15551234567", decomposed.phoneValue)
        assertEquals(true, decomposed.phoneVerified)
        assertEquals("a@b.com", decomposed.emailValue)
        assertEquals(false, decomposed.emailVerified)
        assertEquals(1, decomposed.socialAccounts?.size)
        assertNotNull(decomposed.profilePicture)
        assertNull(decomposed.pendingMigrationJson) // staging cleared
    }
}

@RunWith(RobolectricTestRunner::class)
class UserProfileDaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = db.close()

    private fun fullProfile(userId: String) = UserProfileEntity(
        userIdHex = userId,
        displayName = "Alice",
        phoneValue = "+15551234567", phoneVerified = true,
        emailValue = "a@b.com", emailVerified = false,
        socialAccounts = emptyList(),
        profilePicture = MediaItem(renditions = emptyList()),
        username = "alice",
    )

    @Test
    fun `partial name+avatar write does not downgrade a richer chat profile`() = runBlocking {
        val dao = db.userProfileDao()
        dao.upsertFull(listOf(fullProfile("u1")))

        // Blocklist sync only knows name + avatar — must not wipe phone/email/social.
        dao.upsertNameAndAvatar(userIdHex = "u1", displayName = "Alice (blocked)", profilePicture = null)

        db.openHelper.writableDatabase.query("SELECT display_name, phone_value, email_value FROM user_profiles WHERE user_id_hex = 'u1'").use {
            it.moveToFirst()
            assertEquals("Alice (blocked)", it.getString(0)) // name updated
            assertEquals("+15551234567", it.getString(1))    // phone preserved
            assertEquals("a@b.com", it.getString(2))          // email preserved
        }
    }

    @Test
    fun `partial name+avatar write preserves the cached username`() = runBlocking {
        val dao = db.userProfileDao()
        dao.upsertFull(listOf(fullProfile("u1")))

        // The blocklist has no username to write; the handle must survive its sync.
        dao.upsertNameAndAvatar(userIdHex = "u1", displayName = "Alice (blocked)", profilePicture = null)

        assertEquals("alice", dao.getByUserId("u1")?.username)
    }

    @Test
    fun `chat member relation joins the shared normalized profile`() = runBlocking {
        db.userProfileDao().upsertFull(listOf(fullProfile("u1")))
        db.chatMemberDao().upsert(ChatMemberEntity(chatIdHex = "chatA", userIdHex = "u1", pointersJson = null))

        val members = db.chatMemberDao().getMembersForChat("chatA")

        assertEquals(1, members.size)
        assertEquals("u1", members.first().member.userIdHex)
        assertEquals("Alice", members.first().profile?.displayName)
    }
}
