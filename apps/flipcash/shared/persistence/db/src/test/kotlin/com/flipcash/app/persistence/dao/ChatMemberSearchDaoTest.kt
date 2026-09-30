package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The queries the member search runs. Tokens are written already normalized here; how names become
 * tokens is `MemberSearchText`'s concern, tested in the sources module.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMemberSearchDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMemberSearchDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMemberSearchDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun member(userIdHex: String, name: String, vararg tokens: String, chatIdHex: String = CHAT) {
        db.userProfileDao().upsertFull(listOf(profile(userIdHex, name)))
        db.chatMemberDao().upsert(ChatMemberEntity(chatIdHex = chatIdHex, userIdHex = userIdHex, pointersJson = null))
        dao.replaceTokens(chatIdHex, userIdHex, tokens.toList())
    }

    private suspend fun message(id: Long, senderHex: String, at: Long) {
        db.chatMessageDao().insert(
            ChatMessageEntity(
                chatIdHex = CHAT,
                messageId = id,
                senderIdHex = senderHex,
                contentJson = null,
                timestampEpochMs = at,
                unreadSeq = 0,
            )
        )
    }

    private suspend fun search(prefix: String, window: Int = 50) =
        dao.searchByTokenRange(CHAT, SELF, prefix, prefix + UPPER, window)

    @Test
    fun `a prefix finds members with any word starting with it`() = runTest {
        member("a1", "Érica Stone", "erica", "stone")
        member("b2", "Mark Sterling", "mark", "sterling")
        member("c3", "Alan", "alan")

        assertEquals(setOf("a1", "b2"), search("st").map { it.userIdHex }.toSet())
        assertEquals(listOf("a1"), search("eri").map { it.userIdHex })
    }

    @Test
    fun `a member matching on two words comes back once`() = runTest {
        member("a1", "Sam Samuels", "sam", "samuels")

        assertEquals(listOf("a1"), search("sam").map { it.userIdHex })
    }

    @Test
    fun `the current user is never returned`() = runTest {
        member(SELF, "Erin", "erin")
        member("a1", "Eric", "eric")

        assertEquals(listOf("a1"), search("er").map { it.userIdHex })
        message(1, SELF, at = 10)
        assertEquals(emptyList(), dao.recentSpeakers(CHAT, SELF, 50).map { it.userIdHex })
    }

    @Test
    fun `other chats' members are not searched`() = runTest {
        member("a1", "Eric", "eric", chatIdHex = "other")

        assertEquals(emptyList(), search("er"))
    }

    @Test
    fun `last spoke is the sender's newest message within the window`() = runTest {
        member("a1", "Eric", "eric")
        member("b2", "Erin", "erin")
        message(1, "b2", at = 10)
        message(2, "a1", at = 20)
        message(3, "a1", at = 30)

        val byId = search("er").associate { it.userIdHex to it.lastSpokeEpochMs }
        assertEquals(30L, byId["a1"])
        assertEquals(10L, byId["b2"])

        // A window of the two newest messages leaves b2 out.
        val narrow = search("er", window = 2).associate { it.userIdHex to it.lastSpokeEpochMs }
        assertEquals(null, narrow["b2"])
        assertEquals(listOf("a1"), dao.recentSpeakers(CHAT, SELF, 2).map { it.userIdHex })
    }

    @Test
    fun `a former member is not a recent speaker`() = runTest {
        message(1, "gone", at = 10)

        assertEquals(emptyList(), dao.recentSpeakers(CHAT, SELF, 50))
    }

    @Test
    fun `a renamed member's old words stop matching`() = runTest {
        member("a1", "Eric", "eric")
        dao.replaceTokens(CHAT, "a1", listOf("frank"))

        assertEquals(emptyList(), search("er"))
        assertEquals(listOf("a1"), search("fr").map { it.userIdHex })
    }

    @Test
    fun `a profile rewrite reaches every chat the member is in`() = runTest {
        member("a1", "Eric", "eric")
        member("a1", "Eric", "eric", chatIdHex = "other")

        dao.replaceTokensEverywhere("a1", listOf("frank"))

        assertEquals(listOf("frank"), dao.getTokens(CHAT, "a1"))
        assertEquals(listOf("frank"), dao.getTokens("other", "a1"))
    }

    @Test
    fun `the prefix lookup is an index range scan`() {
        val plan = db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT user_id_hex FROM chat_member_search_tokens " +
                "WHERE chat_id_hex = ? AND token >= ? AND token < ?",
            arrayOf(CHAT, "er", "er$UPPER"),
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) }
        }.joinToString("\n")

        // A SEARCH bounded on both columns, not a SCAN of the table or the index.
        assertTrue(
            Regex("SEARCH .*INDEX index_chat_member_search_tokens_chat_id_hex_token \\(chat_id_hex=\\? AND token>\\? AND token<\\?\\)")
                .containsMatchIn(plan),
            plan,
        )
    }

    @Test
    fun `flagging a chat never read creates its row`() = runTest {
        dao.markNeedsResync(CHAT)

        assertEquals(true, dao.getSyncState(CHAT)?.needsResync)
    }

    private fun profile(userIdHex: String, name: String) = UserProfileEntity(
        userIdHex = userIdHex,
        displayName = name,
        phoneValue = null,
        phoneVerified = null,
        emailValue = null,
        emailVerified = null,
        socialAccounts = null,
        profilePicture = null,
    )

    private companion object {
        const val CHAT = "c0ffee"
        const val SELF = "5e1f"
        const val UPPER = "􏿿" // U+10FFFF, as MemberSearchText.UPPER_BOUND
    }
}
