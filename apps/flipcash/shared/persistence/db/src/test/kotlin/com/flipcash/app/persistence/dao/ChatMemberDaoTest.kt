package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers what a feed sync is allowed to overwrite on a member row. `pointers_json` holds the
 * READ pointer, which the client advances locally the moment a message is seen and only then
 * reports to the server. A feed payload is server truth as of the fetch, so a whole-row replace
 * rewinds an advance the server has not acknowledged yet and the chat re-reports as unread.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMemberDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMemberDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMemberDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun readPointer(value: Long, userIdHex: String = SELF_HEX) =
        MessagePointerSerialized(type = "READ", userIdHex = userIdHex, value = value)

    private fun member(vararg pointers: MessagePointerSerialized) = ChatMemberEntity(
        chatIdHex = CHAT_HEX,
        userIdHex = SELF_HEX,
        pointersJson = pointers.toList(),
    )

    @Test
    fun `upsert does not rewind a locally advanced read pointer`() = runTest {
        dao.upsert(member(readPointer(1)))
        dao.advancePointer(CHAT_HEX, SELF_HEX, readPointer(9))

        // A feed sync issued before the advance reached the server carries the old pointer.
        dao.upsert(member(readPointer(1)))

        assertEquals(9L, dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson?.single()?.value)
    }

    @Test
    fun `upsert applies a server pointer that is ahead of the local one`() = runTest {
        dao.upsert(member(readPointer(1)))

        // Read on another device: the server is ahead and must win.
        dao.upsert(member(readPointer(12)))

        assertEquals(12L, dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson?.single()?.value)
    }

    @Test
    fun `upsert keeps a local pointer while applying the other member's`() = runTest {
        dao.upsert(member(readPointer(9), readPointer(3, OTHER_HEX)))

        // The server has seen the peer read further, but not yet our own advance to 9.
        dao.upsert(member(readPointer(1), readPointer(7, OTHER_HEX)))

        val pointers = dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson.orEmpty()
        assertEquals(9L, pointers.single { it.userIdHex == SELF_HEX }.value)
        assertEquals(7L, pointers.single { it.userIdHex == OTHER_HEX }.value)
    }

    @Test
    fun `advancePointer records a read for a member that has not synced yet`() = runTest {
        // The chat is open and a message is on screen before the feed writes the membership.
        dao.advancePointer(CHAT_HEX, SELF_HEX, readPointer(4))

        assertEquals(4L, dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson?.single()?.value)
    }

    @Test
    fun `advancePointer leaves the member's other pointers alone`() = runTest {
        dao.upsert(member(readPointer(2), MessagePointerSerialized("DELIVERED", SELF_HEX, 5)))

        dao.advancePointer(CHAT_HEX, SELF_HEX, readPointer(6))

        val pointers = dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson.orEmpty()
        assertEquals(6L, pointers.single { it.type == "READ" }.value)
        assertEquals(5L, pointers.single { it.type == "DELIVERED" }.value)
    }

    @Test
    fun `advancePointer does not lower a pointer that is already ahead`() = runTest {
        dao.advancePointer(CHAT_HEX, SELF_HEX, readPointer(9))

        // The stream echoes our own READ pointer as the server last saw it, behind the local one.
        dao.advancePointer(CHAT_HEX, SELF_HEX, readPointer(4))

        assertEquals(9L, dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson?.single()?.value)
    }

    @Test
    fun `deleteMembersNotIn drops departed members and leaves the rest intact`() = runTest {
        dao.upsert(member(readPointer(9)))
        dao.upsert(member(readPointer(4, OTHER_HEX)).copy(userIdHex = OTHER_HEX))

        dao.deleteMembersNotIn(CHAT_HEX, listOf(SELF_HEX))

        assertNull(dao.getMember(CHAT_HEX, OTHER_HEX))
        assertEquals(9L, dao.getMember(CHAT_HEX, SELF_HEX)?.pointersJson?.single()?.value)
    }

    private companion object {
        const val CHAT_HEX = "aabb"
        const val SELF_HEX = "ccdd"
        const val OTHER_HEX = "eeff"
    }
}
