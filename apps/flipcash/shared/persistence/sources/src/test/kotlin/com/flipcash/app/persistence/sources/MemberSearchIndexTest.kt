package com.flipcash.app.persistence.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.app.persistence.sources.search.MemberSearchText
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The search index follows every member write: a roster read, a join or leave off the stream, and a
 * profile resolved on its own. Run against the real database, since the index is kept in step inside
 * the same transactions as the member rows.
 */
@RunWith(RobolectricTestRunner::class)
class MemberSearchIndexTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mapper = ChatEntityMapper()
    private val members = ChatMemberDataSource(mapper)
    private val roster = ChatRosterDataSource(mapper)
    private val profiles = UserProfileDataSource()

    @Before
    fun setUp() {
        FlipcashDatabase.init(context, ENTROPY)
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
        context.databaseList().forEach { context.deleteDatabase(it) }
    }

    private suspend fun search(query: String): Set<ID> =
        roster.searchByPrefix(CHAT, SELF, MemberSearchText.normalize(query), 50).map { it.userId }.toSet()

    @Test
    fun `a joined member is searchable by any name word and by handle`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Érica Stone", "estone")))

        assertEquals(setOf(ERICA), search("eri"))
        assertEquals(setOf(ERICA), search("STO"))
        assertEquals(setOf(ERICA), search("est"))
        assertEquals(emptySet(), search("ica"))
    }

    @Test
    fun `a member who left is no longer found`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica"), member(ERIN, "Erin")))

        members.deleteMember(CHAT, ERICA)

        assertEquals(setOf(ERIN), search("eri"))
    }

    @Test
    fun `a full read drops members it did not return`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica", version = 3), member(ERIN, "Erin", version = 4)))

        members.reconcile(CHAT, seen = setOf(ERIN), readVersion = 7)

        assertEquals(setOf(ERIN), search("eri"))
        assertEquals(1, members.countMembers(CHAT))
    }

    @Test
    fun `a full read keeps a member who joined after the version it read`() = runTest {
        // Erica joined at 9 off the stream; the read described the roster at 7, before she was in it.
        members.upsert(CHAT, listOf(member(ERICA, "Erica", version = 9), member(ERIN, "Erin", version = 4)))

        members.reconcile(CHAT, seen = setOf(ERIN), readVersion = 7)

        assertEquals(setOf(ERICA, ERIN), search("eri"))
    }

    @Test
    fun `a trailing page does not wind a member's version back`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica", version = 9)))
        members.upsert(CHAT, listOf(member(ERICA, "Erica", version = 2)))

        // Still above the read, so still kept.
        members.reconcile(CHAT, seen = emptySet(), readVersion = 7)

        assertEquals(setOf(ERICA), search("eri"))
    }

    @Test
    fun `a renamed member is found by the new name only`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica")))

        profiles.store(ERICA, profile("Frankie", username = null))

        assertEquals(emptySet(), search("eri"))
        assertEquals(setOf(ERICA), search("fra"))
    }

    @Test
    fun `a member re-sent without a profile keeps the words already held`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica")))

        members.upsert(CHAT, listOf(member(ERICA, "")))

        assertEquals(setOf(ERICA), search("eri"))
    }

    @Test
    fun `the current user is never a result`() = runTest {
        members.upsert(CHAT, listOf(member(SELF, "Eric"), member(ERIN, "Erin")))

        assertEquals(setOf(ERIN), search("eri"))
    }

    @Test
    fun `closing a chat clears its index and sync state`() = runTest {
        members.upsert(CHAT, listOf(member(ERICA, "Erica")))
        roster.markFullySynced(CHAT, watermark = 4, truncated = false)

        members.deleteForChat(CHAT)

        assertEquals(emptySet(), search("eri"))
        assertEquals(null, roster.getSyncState(CHAT))
    }

    private fun profile(name: String, username: String?) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        username = username,
    )

    private fun member(id: ID, name: String, username: String? = null, version: Long = 0) =
        ChatMember(userId = id, userProfile = profile(name, username), pointers = emptyList(), version = version)

    private companion object {
        const val ENTROPY = "bWVtYmVyLXNlYXJjaC1pbmRleC10ZXN0"
        val CHAT = ChatId(listOf<Byte>(0x0C, 0x0F))
        val SELF: ID = listOf(0x01)
        val ERICA: ID = listOf(0x0E, 0x01)
        val ERIN: ID = listOf(0x0E, 0x02)
    }
}
