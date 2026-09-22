package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.ChatRulesSerialized
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.models.chat.MediaItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers what a feed sync is allowed to overwrite. `latest_event_sequence` (the applied
 * catch-up cursor) and `analytics_counted_through` (the received-message replay guard) are
 * client-owned watermarks that no server payload carries, so an upsert of server truth must
 * leave them alone — a whole-row replace would rewind both.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMetadataDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMetadataDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        chatIdHex: String = CHAT_HEX,
        chatType: String = "CONTACT_DM",
        lastActivityEpochMs: Long = 1_000,
        lastMessageId: Long? = null,
        latestEventSequence: Long = 0,
        isHidden: Boolean = false,
        analyticsCountedThrough: Long = 0,
        title: String? = null,
        pictureJson: MediaItem? = null,
        memberCount: Long = 0,
        rosterVersion: Long = 0,
        rulesJson: ChatRulesSerialized? = null,
        isMember: Boolean = true,
        muteUntilEpochMs: Long? = null,
        muteForever: Boolean = false,
        viewerStateVersion: Long = 0,
        canEdit: Boolean = false,
    ) = ChatMetadataEntity(
        chatIdHex = chatIdHex,
        chatType = chatType,
        lastActivityEpochMs = lastActivityEpochMs,
        lastMessageId = lastMessageId,
        latestEventSequence = latestEventSequence,
        isHidden = isHidden,
        analyticsCountedThrough = analyticsCountedThrough,
        title = title,
        pictureJson = pictureJson,
        memberCount = memberCount,
        rosterVersion = rosterVersion,
        rulesJson = rulesJson,
        isMember = isMember,
        muteUntilEpochMs = muteUntilEpochMs,
        muteForever = muteForever,
        viewerStateVersion = viewerStateVersion,
        canEdit = canEdit,
    )

    @Test
    fun `upsert preserves the client-owned watermarks`() = runTest {
        dao.upsert(entity())
        dao.updateLatestEventSequence(CHAT_HEX, 9)
        dao.advanceAnalyticsCountedThrough(CHAT_HEX, 42)

        // A later feed sync carries neither watermark — the mapper never sets them.
        dao.upsert(entity(lastActivityEpochMs = 2_000, lastMessageId = 77))

        assertEquals(9L, dao.getLatestEventSequence(CHAT_HEX))
        assertEquals(42L, dao.getAnalyticsCountedThrough(CHAT_HEX))
    }

    @Test
    fun `upsert refreshes the server-owned columns`() = runTest {
        dao.upsert(entity(chatType = "CONTACT_DM", lastActivityEpochMs = 1_000, lastMessageId = 1))

        dao.upsert(
            entity(
                chatType = "TIP_DM",
                lastActivityEpochMs = 2_000,
                lastMessageId = 77,
                isHidden = true,
            )
        )

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals("TIP_DM", stored.chatType)
        assertEquals(2_000L, stored.lastActivityEpochMs)
        assertEquals(77L, stored.lastMessageId)
        assertEquals(true, stored.isHidden)
    }

    @Test
    fun `upsert inserts a chat the database has not seen`() = runTest {
        dao.upsert(entity(lastMessageId = 5))

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals(5L, stored.lastMessageId)
        assertEquals(0L, stored.latestEventSequence)
    }

    @Test
    fun `list upsert preserves each row's cursor`() = runTest {
        dao.upsert(listOf(entity(chatIdHex = CHAT_HEX), entity(chatIdHex = OTHER_HEX)))
        dao.updateLatestEventSequence(CHAT_HEX, 9)
        dao.updateLatestEventSequence(OTHER_HEX, 4)

        dao.upsert(
            listOf(
                entity(chatIdHex = CHAT_HEX, lastActivityEpochMs = 2_000),
                entity(chatIdHex = OTHER_HEX, lastActivityEpochMs = 3_000),
            )
        )

        assertEquals(9L, dao.getLatestEventSequence(CHAT_HEX))
        assertEquals(4L, dao.getLatestEventSequence(OTHER_HEX))
    }

    @Test
    fun `group columns round-trip through an insert`() = runTest {
        dao.upsert(
            entity(
                chatType = "GROUP",
                title = "Flipcash Staff",
                memberCount = 12,
                rosterVersion = 4,
                isMember = true,
            )
        )

        val stored = dao.getById(CHAT_HEX)

        assertEquals("Flipcash Staff", stored?.title)
        assertEquals(12L, stored?.memberCount)
        assertEquals(4L, stored?.rosterVersion)
        assertEquals(true, stored?.isMember)
    }

    @Test
    fun `upsert refreshes the flat group columns`() = runTest {
        dao.upsert(entity(chatType = "GROUP", title = "Old title", isMember = true))

        dao.upsert(entity(chatType = "GROUP", title = "New title", isMember = false))

        val stored = dao.getById(CHAT_HEX)
        assertEquals("New title", stored?.title)
        assertEquals(false, stored?.isMember)
    }

    /**
     * `MetadataUpdate.TitleChanged` carries no version, unlike the roster and viewer-state
     * overlays above, so there is nothing to gate the write on.
     */
    @Test
    fun `updateTitle overwrites the title unconditionally`() = runTest {
        dao.upsert(entity(chatType = "GROUP", title = "Old title"))

        dao.updateTitle(CHAT_HEX, "New title")

        assertEquals("New title", dao.getById(CHAT_HEX)?.title)
    }

    /** Same unconditional contract as `updateTitle`, for `MetadataUpdate.PictureChanged`. */
    @Test
    fun `updatePicture overwrites the picture unconditionally`() = runTest {
        dao.upsert(entity(chatType = "GROUP"))
        val picture = MediaItem(renditions = emptyList())

        dao.updatePicture(CHAT_HEX, picture)

        assertEquals(picture, dao.getById(CHAT_HEX)?.pictureJson)
    }

    @Test
    fun `a newer roster version replaces the roster columns`() = runTest {
        dao.upsert(entity(chatType = "GROUP", memberCount = 12, rosterVersion = 4))

        dao.upsert(entity(chatType = "GROUP", memberCount = 13, rosterVersion = 5))

        val stored = dao.getById(CHAT_HEX)
        assertEquals(13L, stored?.memberCount)
        assertEquals(5L, stored?.rosterVersion)
    }

    @Test
    fun `an older roster version leaves the roster columns alone`() = runTest {
        dao.upsert(entity(chatType = "GROUP", memberCount = 12, rosterVersion = 4))

        dao.upsert(entity(chatType = "GROUP", memberCount = 0, rosterVersion = 0))

        val stored = dao.getById(CHAT_HEX)
        assertEquals(12L, stored?.memberCount)
        assertEquals(4L, stored?.rosterVersion)
    }

    @Test
    fun `a newer viewer state version replaces the mute columns`() = runTest {
        dao.upsert(entity(chatType = "GROUP", muteUntilEpochMs = 5_000, viewerStateVersion = 4))

        dao.updateViewerStateIfNewer(
            chatIdHex = CHAT_HEX,
            muteUntilEpochMs = null,
            muteForever = false,
            canEdit = false,
            version = 5,
        )

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertNull(stored.muteUntilEpochMs)
        assertEquals(false, stored.muteForever)
        assertEquals(5L, stored.viewerStateVersion)
    }

    /**
     * The stream does not order deliveries, so a mute and the unmute that followed it can arrive
     * either way round. Applying whichever landed last would leave the wrong answer stored, which
     * is why the version and not the arrival decides.
     */
    @Test
    fun `an older viewer state version leaves the mute alone`() = runTest {
        dao.upsert(entity(chatType = "GROUP", viewerStateVersion = 0))
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = true, canEdit = false, version = 5)

        // The mute this supersedes, arriving late.
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = 5_000, muteForever = false, canEdit = false, version = 4)

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals(true, stored.muteForever)
        assertNull(stored.muteUntilEpochMs)
        assertEquals(5L, stored.viewerStateVersion)
    }

    /**
     * The same gate an upsert goes through. A `ChatMetadata` rebuilt from this row reports the
     * version it was read at, so pushing it back must be a no-op rather than a re-application.
     */
    @Test
    fun `an upsert at the stored version does not rewrite the mute`() = runTest {
        dao.upsert(entity(chatType = "GROUP", muteForever = true, viewerStateVersion = 7))

        dao.upsert(entity(chatType = "GROUP", muteUntilEpochMs = null, muteForever = false, viewerStateVersion = 7))

        assertEquals(true, dao.getById(CHAT_HEX)?.muteForever)
    }

    @Test
    fun `switching to an indefinite mute clears the deadline`() = runTest {
        dao.upsert(entity(chatType = "GROUP", muteUntilEpochMs = 5_000, viewerStateVersion = 1))

        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = true, canEdit = false, version = 2)

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertNull(stored.muteUntilEpochMs)
        assertEquals(true, stored.muteForever)
    }

    /**
     * Leaving clears the mute server-side and says nothing about it, and a rejoin starts the
     * server's versioning over — so a version left behind would make the gate reject the new
     * state and the old mute would survive the chat it belonged to.
     */
    @Test
    fun `clearing the viewer state drops the mute and its version`() = runTest {
        dao.upsert(entity(chatType = "GROUP", muteForever = true, viewerStateVersion = 7))

        dao.clearViewerState(CHAT_HEX)

        val stored = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals(false, stored.muteForever)
        assertNull(stored.muteUntilEpochMs)
        assertEquals(0L, stored.viewerStateVersion)

        // A rejoin's first state numbers from the bottom again, and must still apply.
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = 9_000, muteForever = false, canEdit = false, version = 1)
        assertEquals(9_000L, dao.getById(CHAT_HEX)?.muteUntilEpochMs)
    }

    /**
     * The grant carries no version of its own, so it is only ever as current as the viewer state
     * that delivered it. A payload that loses the version race must not repaint it.
     */
    @Test
    fun `an older viewer state leaves the edit grant alone`() = runTest {
        dao.upsert(entity(chatType = "GROUP", viewerStateVersion = 4))
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = false, canEdit = true, version = 5)

        // The withdrawal this supersedes, arriving late.
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = false, canEdit = false, version = 3)

        assertEquals(true, dao.getById(CHAT_HEX)?.canEdit)
    }

    /**
     * A withdrawn grant has to reach the row. Ownership moves, and an Edit affordance left on
     * screen offers an action the server answers with DENIED.
     */
    @Test
    fun `a newer viewer state withdraws the edit grant`() = runTest {
        dao.upsert(entity(chatType = "GROUP", canEdit = true, viewerStateVersion = 4))

        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = false, canEdit = false, version = 5)

        assertEquals(false, dao.getById(CHAT_HEX)?.canEdit)
    }

    /** A chat the user has left grants nothing. */
    @Test
    fun `clearing the viewer state drops the edit grant`() = runTest {
        dao.upsert(entity(chatType = "GROUP", canEdit = true, viewerStateVersion = 7))

        dao.clearViewerState(CHAT_HEX)

        assertEquals(false, dao.getById(CHAT_HEX)?.canEdit)
    }

    /**
     * Rows migrated from v36 hold the new column at 0 and the version the server last sent. The
     * next fetch reports that same version, so without the re-arm it loses the gate and the grant
     * stays 0 — the Edit affordance missing on every chat that predates the upgrade.
     */
    @Test
    fun `the v37 re-arm lets the next fetch fill the edit grant`() = runTest {
        dao.upsert(entity(chatType = "GROUP", viewerStateVersion = 7))

        db.openHelper.writableDatabase.execSQL(FlipcashDatabase.Migration36To37.REARM_VIEWER_STATE)

        // The version the row already held, which the gate would otherwise drop.
        dao.updateViewerStateIfNewer(CHAT_HEX, muteUntilEpochMs = null, muteForever = false, canEdit = true, version = 7)

        assertEquals(true, dao.getById(CHAT_HEX)?.canEdit)
    }

    @Test
    fun `the viewer state version reads back, and is null for a chat that is not there`() = runTest {
        dao.upsert(entity(chatType = "GROUP", muteForever = true, viewerStateVersion = 7))

        assertEquals(7L, dao.getViewerStateVersion(CHAT_HEX))
        assertEquals(null, dao.getViewerStateVersion(OTHER_HEX))
    }

    /**
     * One ordering across every type is the whole point of the merged feed: a group that belongs
     * between two DMs has to come back between them, not after all of them.
     */
    @Test
    fun `the paged feed orders every type together, newest first`() = runTest {
        dao.upsert(entity(chatIdHex = "01", chatType = "CONTACT_DM", lastActivityEpochMs = 3_000))
        dao.upsert(entity(chatIdHex = "02", chatType = "GROUP", lastActivityEpochMs = 2_000))
        dao.upsert(entity(chatIdHex = "03", chatType = "TIP_DM", lastActivityEpochMs = 1_000))

        val page = dao.observeFeedPaged(listOf("CONTACT_DM", "TIP_DM", "GROUP")).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("01", "02", "03"), page.data.map { it.chatIdHex })
    }

    @Test
    fun `the paged feed leaves out a type the caller did not ask for`() = runTest {
        dao.upsert(entity(chatIdHex = "01", chatType = "CONTACT_DM", lastActivityEpochMs = 3_000))
        dao.upsert(entity(chatIdHex = "02", chatType = "GROUP", lastActivityEpochMs = 2_000))

        val page = dao.observeFeedPaged(listOf("CONTACT_DM")).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("01"), page.data.map { it.chatIdHex })
    }

    @Test
    fun `the paged feed leaves out hidden chats and chats you are not in`() = runTest {
        dao.upsert(entity(chatIdHex = "01", chatType = "GROUP", lastActivityEpochMs = 3_000))
        dao.upsert(entity(chatIdHex = "02", chatType = "GROUP", lastActivityEpochMs = 2_000, isHidden = true))
        dao.upsert(entity(chatIdHex = "03", chatType = "GROUP", lastActivityEpochMs = 1_000, isMember = false))

        val page = dao.observeFeedPaged(listOf("GROUP")).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("01"), page.data.map { it.chatIdHex })
    }

    /**
     * Two chats can share a last-activity timestamp, and `ORDER BY` alone would then leave their
     * relative order up to SQLite. Paging asks for the next page by offset, so an unstable order
     * duplicates one row and drops the other.
     */
    @Test
    fun `chats sharing a last activity break the tie on chat id`() = runTest {
        dao.upsert(entity(chatIdHex = "01", lastActivityEpochMs = 3_000))
        dao.upsert(entity(chatIdHex = "02", lastActivityEpochMs = 3_000))
        dao.upsert(entity(chatIdHex = "03", lastActivityEpochMs = 3_000))

        val page = dao.observeFeedPaged(listOf("CONTACT_DM")).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("03", "02", "01"), page.data.map { it.chatIdHex })
    }

    /**
     * What the mediator compares a complete pass against. Only the chats the caller is still in —
     * one already cleared is not a removal to rediscover.
     */
    @Test
    fun `the id listing covers the chats of one type you are still in`() = runTest {
        dao.upsert(entity(chatIdHex = "01", chatType = "GROUP"))
        dao.upsert(entity(chatIdHex = "02", chatType = "GROUP", isMember = false))
        dao.upsert(entity(chatIdHex = "03", chatType = "CONTACT_DM"))

        assertEquals(listOf("01"), dao.getChatIdsOfType("GROUP"))
    }

    @Test
    fun `membership can be taken away and given back without touching the row`() = runTest {
        dao.upsert(entity(chatType = "GROUP", title = "Flipcash Staff", memberCount = 12, rosterVersion = 4))

        dao.updateMembership(CHAT_HEX, isMember = false)

        val left = requireNotNull(dao.getById(CHAT_HEX))
        assertEquals(false, left.isMember)
        assertEquals("Flipcash Staff", left.title)
        assertEquals(12L, left.memberCount)

        dao.updateMembership(CHAT_HEX, isMember = true)
        assertEquals(true, dao.getById(CHAT_HEX)?.isMember)
    }

    @Test
    fun `the roster version reads back, and is null for a chat that is not there`() = runTest {
        dao.upsert(entity(chatType = "GROUP", memberCount = 12, rosterVersion = 4))

        assertEquals(4L, dao.getRosterVersion(CHAT_HEX))
        assertEquals(null, dao.getRosterVersion(OTHER_HEX))
    }

    @Test
    fun `observeById re-emits when the row changes`() = runTest {
        dao.upsert(entity(title = "Flipcash Staff", memberCount = 2, rosterVersion = 1))

        dao.observeById(CHAT_HEX).test {
            assertEquals(2L, awaitItem()?.memberCount)

            // Through `updateRosterIfNewer`, not `upsert`: the roster columns are versioned and
            // an upsert deliberately leaves them alone, so this is the write a roster event makes.
            dao.updateRosterIfNewer(CHAT_HEX, memberCount = 3, rosterVersion = 2)
            assertEquals(3L, awaitItem()?.memberCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeById emits null for a chat this device has never stored`() = runTest {
        dao.observeById(CHAT_HEX).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val CHAT_HEX = "aabbccdd"
        const val OTHER_HEX = "eeff0011"
    }
}
