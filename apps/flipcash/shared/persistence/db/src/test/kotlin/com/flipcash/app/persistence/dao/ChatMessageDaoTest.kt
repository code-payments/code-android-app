package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.EmojiReactionSerialized
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.ReactionSummarySerialized
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.EncryptionState
import com.flipcash.app.persistence.entities.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Covers the split between the newest stored row and the newest row that still has content. The
 * conversation list previews the latter so a delete falls back to the message before it; mark-read
 * and the receive buzz anchor on the former so a delete can't rewind the read pointer.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMessageDaoTest {

    private val reactionJsonCodec = Json { ignoreUnknownKeys = true }

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMessageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun text(messageId: Long, body: String) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text(body)),
        timestampEpochMs = messageId * 1_000,
        unreadSeq = messageId,
    )

    private fun pending(body: String) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = -1,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text(body)),
        timestampEpochMs = 1,
        unreadSeq = 0,
        status = MessageStatus.SENDING,
        pendingClientIdHex = CLIENT_HEX,
    )

    private fun tombstone(messageId: Long) = text(messageId, "gone").copy(
        contentJson = listOf(MessageContentSerialized.Deleted(deletedAt = 1, deletedBy = SENDER_HEX)),
        isDeleted = true,
    )

    @Test
    fun `getLatestVisible skips the tombstone the newest row became`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(tombstone(2))

        assertEquals(2L, dao.getLatest(CHAT_HEX)?.messageId)
        assertEquals(1L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible returns the newest row when nothing is deleted`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))

        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible is null once every message is deleted`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(listOf(tombstone(1), tombstone(2)))

        assertEquals(2L, dao.getLatest(CHAT_HEX)?.messageId)
        assertNull(dao.getLatestVisible(CHAT_HEX))
    }

    /**
     * The 31 -> 32 backfill has only the serialized blob to go on, so it matches the discriminator
     * kotlinx.serialization writes for a tombstone. This pins that string to what the converters
     * actually produce — and to what they don't produce for a message that still has content.
     */
    @Test
    fun `the migration backfill flags stored tombstones and leaves text alone`() = runTest {
        // The pre-32 shape: the flag defaulted to 0 for every cached row.
        dao.upsert(listOf(text(1, "one"), tombstone(2).copy(isDeleted = false)))
        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)

        db.openHelper.writableDatabase.execSQL(FlipcashDatabase.Migration31To32.BACKFILL_TOMBSTONES)

        assertEquals(1L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible ignores other chats`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(text(3, "elsewhere").copy(chatIdHex = OTHER_HEX))

        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisibleForAllChats gives each chat its own latest visible message`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(tombstone(2))
        dao.upsert(listOf(text(3, "three"), text(4, "four")).map { it.copy(chatIdHex = OTHER_HEX) })

        val latest = dao.getLatestVisibleForAllChats().associate { it.chatIdHex to it.messageId }

        assertEquals(mapOf(CHAT_HEX to 1L, OTHER_HEX to 4L), latest)
    }

    @Test
    fun `getLatestVisibleForAllChats leaves out a chat whose messages are all deleted`() = runTest {
        dao.upsert(tombstone(1))
        dao.upsert(text(2, "two").copy(chatIdHex = OTHER_HEX))

        assertEquals(listOf(OTHER_HEX), dao.getLatestVisibleForAllChats().map { it.chatIdHex })
    }

    @Test
    fun `on a shared timestamp both latest-visible reads pick the highest message id`() = runTest {
        val sameMs = 5_000L
        dao.upsert(listOf(3L, 1L, 2L).map { text(it, "m$it").copy(timestampEpochMs = sameMs) })

        assertEquals(3L, dao.getLatestVisible(CHAT_HEX)?.messageId)
        assertEquals(listOf(3L), dao.getLatestVisibleForAllChats().map { it.messageId })
    }

    /**
     * The optimistic row is written with no event sequence, because the client has none to write:
     * the server stamps it. Confirming has to carry the echo's stamp onto the row, or the message
     * stays at sequence 0 until something else refetches the chat — and everything keyed on the
     * stamp (edit, delete, last-writer-wins) treats it as unacknowledged in the meantime.
     */
    @Test
    fun `confirming a pending message carries the server's event sequence`() = runTest {
        dao.upsert(pending("hello"))

        dao.confirmPendingMessage(
            CHAT_HEX,
            CLIENT_HEX,
            text(7, "hello").copy(eventSequence = 42, unreadSeq = 3),
        )

        val stored = dao.getMessage(CHAT_HEX, 7)!!
        assertEquals(42L, stored.eventSequence)
        assertEquals(MessageStatus.SENT, stored.status)
        assertEquals(3L, stored.unreadSeq)
    }

    /**
     * Two messages can share a millisecond — a burst send, or a server batch stamped from one clock
     * read. `timestamp_epoch_ms DESC` alone leaves their order to SQLite, so a paged read that
     * re-queries per page can hand back the same row twice or skip one. The tie-breaker makes the
     * order total.
     */
    @Test
    fun `messages sharing a timestamp order by message id, newest first`() = runTest {
        val sameMs = 5_000L
        dao.upsert(
            listOf(
                text(1, "first").copy(timestampEpochMs = sameMs),
                text(2, "second").copy(timestampEpochMs = sameMs),
                text(3, "third").copy(timestampEpochMs = sameMs),
            )
        )

        val page = dao.observeMessagesPaged(CHAT_HEX).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf(3L, 2L, 1L), page.data.map { it.messageId })
    }

    /**
     * How far back a message sits from the newest, which is what bounds the jump walk. Counts
     * strictly newer rows, so the newest message is at distance 0.
     */
    @Test
    fun `countNewerThan measures the distance back to a message`() = runTest {
        dao.upsert((1L..5L).map { text(it, "m$it") })

        assertEquals(0, dao.countNewerThan(CHAT_HEX, 5 * 1_000))
        assertEquals(2, dao.countNewerThan(CHAT_HEX, 3 * 1_000))
        assertEquals(4, dao.countNewerThan(CHAT_HEX, 1 * 1_000))
    }

    @Test
    fun `countNewerThan ignores other chats`() = runTest {
        dao.upsert((1L..5L).map { text(it, "m$it") })
        dao.upsert(text(9, "elsewhere").copy(chatIdHex = OTHER_HEX))

        assertEquals(2, dao.countNewerThan(CHAT_HEX, 3 * 1_000))
    }

    /**
     * The unread divider's count, against the cases iOS encodes too: someone else's messages past
     * the viewer's READ pointer, without tombstones. The read-through message itself need not be
     * stored for the range to count.
     */
    private fun from(sender: String, messageId: Long) = text(messageId, "m$messageId").copy(senderIdHex = sender)

    private suspend fun unread(pointer: Long) = dao.countInboundAfter(CHAT_HEX, SELF_HEX, pointer)

    @Test
    fun `countInboundAfter counts only others' messages past the pointer`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SENDER_HEX, 3), from(SENDER_HEX, 4)))

        assertEquals(2, unread(pointer = 2))
    }

    @Test
    fun `countInboundAfter is zero when nothing is past the pointer`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SENDER_HEX, 2)))

        assertEquals(0, unread(pointer = 2))
    }

    @Test
    fun `countInboundAfter ignores the viewer's own messages`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SELF_HEX, 3)))

        assertEquals(0, unread(pointer = 1))
    }

    @Test
    fun `countInboundAfter counts past a read-through message that is gone`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SENDER_HEX, 3)))

        assertEquals(1, unread(pointer = 2))
    }

    @Test
    fun `countInboundAfter skips an unread tombstone`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SENDER_HEX, 2), from(SENDER_HEX, 3)))
        dao.upsert(tombstone(2))

        assertEquals(1, unread(pointer = 1))
    }

    @Test
    fun `countInboundAfter counts past the viewer's own first message`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SENDER_HEX, 3)))

        assertEquals(1, unread(pointer = 1))
    }

    @Test
    fun `countInboundAfter ignores senderless rows and other chats`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), text(2, "system").copy(senderIdHex = null)))
        dao.upsert(from(SENDER_HEX, 3).copy(chatIdHex = OTHER_HEX))

        assertEquals(0, unread(pointer = 1))
    }

    private suspend fun firstNotOwn(pointer: Long) = dao.firstNotSentByAfter(CHAT_HEX, SELF_HEX, pointer)

    @Test
    fun `firstNotSentByAfter is the first message past the pointer`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SENDER_HEX, 3), from(SENDER_HEX, 4)))

        assertEquals(3L, firstNotOwn(pointer = 2))
    }

    @Test
    fun `firstNotSentByAfter steps over the viewer's own messages after the pointer`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SELF_HEX, 3), from(SENDER_HEX, 4)))

        assertEquals(4L, firstNotOwn(pointer = 1))
    }

    @Test
    fun `firstNotSentByAfter lands on an unread tombstone`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SENDER_HEX, 2), from(SENDER_HEX, 3)))
        dao.upsert(tombstone(2))

        assertEquals(2L, firstNotOwn(pointer = 1))
    }

    @Test
    fun `firstNotSentByAfter is null when only the viewer's messages follow`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SELF_HEX, 3)))
        dao.upsert(from(SENDER_HEX, 9).copy(chatIdHex = OTHER_HEX))

        assertEquals(null, firstNotOwn(pointer = 1))
    }

    @Test
    fun `hasAtOrBelow sees a stored row at or below the id, tombstones included`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 3), from(SENDER_HEX, 4)))
        dao.upsert(from(SENDER_HEX, 1).copy(chatIdHex = OTHER_HEX))

        assertEquals(false, dao.hasAtOrBelow(CHAT_HEX, 2))
        assertEquals(true, dao.hasAtOrBelow(CHAT_HEX, 3))

        dao.upsert(tombstone(3))
        assertEquals(true, dao.hasAtOrBelow(CHAT_HEX, 3))
    }

    @Test
    fun `countAfter counts every stored row past the id`() = runTest {
        dao.upsert(listOf(from(SENDER_HEX, 1), from(SELF_HEX, 2), from(SENDER_HEX, 3), from(SENDER_HEX, 4)))
        dao.upsert(tombstone(3))
        dao.upsert(from(SENDER_HEX, 9).copy(chatIdHex = OTHER_HEX))

        assertEquals(3, dao.countAfter(CHAT_HEX, 1))
    }

    /**
     * Killing the app mid-send leaves the row `SENDING` with nothing alive to move it: failure is
     * only ever written from the `onFailure` of the coroutine that issued the send. The sweep is
     * what a fresh process has instead of that coroutine.
     */
    @Test
    fun `the sweep fails a send a dead process left in flight`() = runTest {
        dao.upsert(pending("half-sent"))

        dao.failInterruptedSends()

        val stored = dao.getByClientId(CHAT_HEX, CLIENT_HEX)!!
        assertEquals(MessageStatus.FAILED, stored.status)
        // The retry re-sends under this id, so losing it would lose the affordance with it.
        assertEquals(CLIENT_HEX, stored.pendingClientIdHex)
    }

    /**
     * The point of failing the row rather than leaving it pending. `deleteAllPending` — which every
     * refresh carrying a self-authored message runs — is scoped to `SENDING`, so a swept row is
     * durable where a pending one is deleted along with the text the user typed.
     */
    @Test
    fun `a swept row survives the refresh that would have deleted it`() = runTest {
        dao.upsert(pending("half-sent"))
        dao.failInterruptedSends()

        dao.upsertAndClearPending(CHAT_HEX, listOf(text(1, "from the server")))

        assertEquals(MessageStatus.FAILED, dao.getByClientId(CHAT_HEX, CLIENT_HEX)?.status)
    }

    @Test
    fun `the sweep leaves a sent message alone`() = runTest {
        dao.upsert(text(1, "already sent"))

        dao.failInterruptedSends()

        assertEquals(MessageStatus.SENT, dao.getMessage(CHAT_HEX, 1)?.status)
    }

    // region One row per client id

    /**
     * The client id is the transcript's item key. A second row under it puts two items under one
     * key in the LazyColumn, which throws on every open of the chat (Bugsnag 6ac2ccde).
     */
    @Test
    fun `a write cannot give a client id to a second row`() = runTest {
        dao.upsert(text(1, "confirmed send").copy(pendingClientIdHex = CLIENT_HEX))

        dao.upsert(text(2, "someone else's claim").copy(pendingClientIdHex = CLIENT_HEX))

        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 1)?.pendingClientIdHex)
        assertNull(dao.getMessage(CHAT_HEX, 2)?.pendingClientIdHex)
    }

    @Test
    fun `a refused claim leaves the row's own client id in place`() = runTest {
        dao.upsert(text(1, "confirmed send").copy(pendingClientIdHex = CLIENT_HEX))
        dao.upsert(text(2, "other send").copy(pendingClientIdHex = "aaaa"))

        dao.upsert(text(2, "edited").copy(pendingClientIdHex = CLIENT_HEX))

        assertEquals("aaaa", dao.getMessage(CHAT_HEX, 2)?.pendingClientIdHex)
        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 1)?.pendingClientIdHex)
    }

    @Test
    fun `a row keeps its own client id when it is rewritten`() = runTest {
        dao.upsert(text(1, "sent").copy(pendingClientIdHex = CLIENT_HEX))

        dao.upsert(text(1, "edited"))

        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 1)?.pendingClientIdHex)
    }

    @Test
    fun `the same client id in another chat is not a conflict`() = runTest {
        dao.upsert(text(1, "here").copy(pendingClientIdHex = CLIENT_HEX))

        dao.upsert(text(1, "there").copy(chatIdHex = OTHER_HEX, pendingClientIdHex = CLIENT_HEX))

        assertEquals(CLIENT_HEX, dao.getMessage(OTHER_HEX, 1)?.pendingClientIdHex)
    }

    /**
     * A page carries old self-sent messages as well as the new send. Only one this device has not
     * stored can be the pending row's server copy; a stored one was confirmed already.
     */
    @Test
    fun `the rescue gives the pending id to the new copy, not a stored message`() = runTest {
        dao.upsert(text(1, "old"))
        dao.upsert(pending("new"))

        dao.upsertRescuingPending(CHAT_HEX, SENDER_HEX, listOf(text(1, "old"), text(2, "new")))

        assertNull(dao.getMessage(CHAT_HEX, 1)?.pendingClientIdHex)
        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 2)?.pendingClientIdHex)
        assertNull(dao.getMessage(CHAT_HEX, -1), "the pending row is replaced by its server copy")
    }

    @Test
    fun `the rescue pairs each send with the copy of the same content`() = runTest {
        dao.upsert(pending("first"))
        dao.upsert(pending("second").copy(messageId = -2, timestampEpochMs = 2, pendingClientIdHex = SECOND_CLIENT_HEX))

        dao.upsertRescuingPending(CHAT_HEX, SENDER_HEX, listOf(text(5, "second"), text(6, "first")))

        assertEquals(SECOND_CLIENT_HEX, dao.getMessage(CHAT_HEX, 5)?.pendingClientIdHex)
        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 6)?.pendingClientIdHex)
    }

    @Test
    fun `the rescue leaves another sender's message alone`() = runTest {
        dao.upsert(pending("mine"))

        dao.upsertRescuingPending(CHAT_HEX, SENDER_HEX, listOf(text(3, "theirs").copy(senderIdHex = "9999")))

        assertNull(dao.getMessage(CHAT_HEX, 3)?.pendingClientIdHex)
    }

    @Test
    fun `an unconfirmed newer send does not take the id of an older send's copy`() = runTest {
        dao.upsert(pending("a"))
        dao.upsert(pending("b").copy(messageId = -2, timestampEpochMs = 2, pendingClientIdHex = SECOND_CLIENT_HEX))

        dao.upsertRescuingPending(CHAT_HEX, SENDER_HEX, listOf(text(5, "a")))

        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 5)?.pendingClientIdHex)
        assertNotEquals(SECOND_CLIENT_HEX, dao.getMessage(CHAT_HEX, 5)?.pendingClientIdHex)
    }

    @Test
    fun `content beats order when another device's message is on the page`() = runTest {
        dao.upsert(pending("mine"))

        dao.upsertRescuingPending(
            CHAT_HEX,
            SENDER_HEX,
            listOf(text(4, "other device"), text(5, "mine"), text(6, "later other device")),
        )

        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 5)?.pendingClientIdHex)
        assertNull(dao.getMessage(CHAT_HEX, 4)?.pendingClientIdHex)
        assertNull(dao.getMessage(CHAT_HEX, 6)?.pendingClientIdHex)
    }

    @Test
    fun `sends whose content differs pair oldest first`() = runTest {
        dao.upsert(pending("local-a"))
        dao.upsert(pending("local-b").copy(messageId = -2, timestampEpochMs = 2, pendingClientIdHex = SECOND_CLIENT_HEX))

        dao.upsertRescuingPending(CHAT_HEX, SENDER_HEX, listOf(text(5, "server-a"), text(6, "server-b")))

        assertEquals(CLIENT_HEX, dao.getMessage(CHAT_HEX, 5)?.pendingClientIdHex)
        assertEquals(SECOND_CLIENT_HEX, dao.getMessage(CHAT_HEX, 6)?.pendingClientIdHex)
    }

    // endregion

    // region Confirming after a mis-tagged rescue

    /**
     * Photo P, then text T. A refresh carries P's server copy but not T's; P's pending row is held
     * back by its upload, T has no content match, so [ChatMessageDao.upsertRescuingPending]'s
     * oldest-first pass gives T's client id to P's copy and drops T's pending row. Built here
     * directly: P's copy at 5, holding T's id ([CLIENT_HEX]), and no row left for T.
     */
    private suspend fun storeMisTaggedPhoto() {
        dao.insert(text(5, "photo").copy(pendingClientIdHex = CLIENT_HEX))
    }

    @Test
    fun `confirming a text not yet stored leaves the photo that took its id`() = runTest {
        storeMisTaggedPhoto()

        dao.confirmPendingMessage(CHAT_HEX, CLIENT_HEX, text(6, "text"))

        val photo = dao.getMessage(CHAT_HEX, 5)!!
        assertEquals(listOf(MessageContentSerialized.Text("photo")), photo.contentJson)
        assertEquals(CLIENT_HEX, photo.pendingClientIdHex)
        assertNull(dao.getMessage(CHAT_HEX, 6))
    }

    @Test
    fun `confirming an encrypted text not yet stored leaves the photo's content alone`() = runTest {
        storeMisTaggedPhoto()

        dao.confirmPendingMessage(
            CHAT_HEX,
            CLIENT_HEX,
            text(6, "text").copy(ciphertextJson = cipherJson, encryptionState = EncryptionState.DECRYPTED),
            replaceContent = true,
        )

        val photo = dao.getMessage(CHAT_HEX, 5)!!
        assertEquals(listOf(MessageContentSerialized.Text("photo")), photo.contentJson)
        assertNull(photo.ciphertextJson)
        assertNull(photo.encryptionState)
        assertNull(dao.getMessage(CHAT_HEX, 6))
    }

    @Test
    fun `confirming a text already stored keeps the photo that took its id`() = runTest {
        storeMisTaggedPhoto()
        dao.insert(text(6, "text"))

        dao.confirmPendingMessage(CHAT_HEX, CLIENT_HEX, text(6, "text"))

        assertEquals(listOf(MessageContentSerialized.Text("photo")), dao.getMessage(CHAT_HEX, 5)?.contentJson)
        assertEquals(listOf(MessageContentSerialized.Text("text")), dao.getMessage(CHAT_HEX, 6)?.contentJson)
    }

    // endregion

    // region Reactions

    private fun reactionsJson(vararg entries: Pair<String, Long>) = reactionJsonCodec.encodeToString(
        ReactionSummarySerialized(
            messageId = 1,
            reactions = entries.map { (emoji, version) ->
                EmojiReactionSerialized(emoji = emoji, count = version, sampleReactors = emptyList(), version = version)
            },
        )
    )

    private fun versionsByEmoji(json: String?) = json
        ?.let { reactionJsonCodec.decodeFromString<ReactionSummarySerialized>(it) }
        ?.reactions
        ?.associate { it.emoji to it.version }
        .orEmpty()

    private fun countsByEmoji(json: String?) = json
        ?.let { reactionJsonCodec.decodeFromString<ReactionSummarySerialized>(it) }
        ?.reactions
        ?.associate { it.emoji to it.count }
        .orEmpty()

    /**
     * A plain content upsert (edit, delivery-status refresh, server echo) carries no reaction data
     * of its own. It must not wipe out a reaction a prior `ReactionUpdate` already confirmed.
     */
    @Test
    fun `upsert without reactions keeps the reactions already stored`() = runTest {
        dao.upsert(text(1, "hi"))
        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 1))

        dao.upsert(text(1, "hi edited"))

        assertEquals(mapOf("👍" to 1L), versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)))
    }

    /** An upsert carrying a newer reaction summary wins over the older one on disk. */
    @Test
    fun `upsert with a newer reaction summary replaces the stale one`() = runTest {
        dao.upsert(text(1, "hi"))
        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 1))

        dao.upsert(text(1, "hi").copy(reactionsJson = reactionsJson("👍" to 2)))

        assertEquals(mapOf("👍" to 2L), versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)))
    }

    /** An upsert carrying a stale reaction summary does not regress the newer one on disk. */
    @Test
    fun `upsert with a stale reaction summary is ignored`() = runTest {
        dao.upsert(text(1, "hi"))
        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 5))

        dao.upsert(text(1, "hi").copy(reactionsJson = reactionsJson("👍" to 2)))

        assertEquals(mapOf("👍" to 5L), versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)))
    }

    @Test
    fun `updateReactionsJson writes reactions without touching other columns`() = runTest {
        dao.upsert(text(1, "hi"))

        dao.updateReactionsJson(CHAT_HEX, 1, reactionsJson("❤️" to 1))

        val stored = dao.getMessage(CHAT_HEX, 1)!!
        assertEquals("hi", (stored.contentJson?.first() as MessageContentSerialized.Text).text)
        assertEquals(mapOf("❤️" to 1L), versionsByEmoji(stored.reactionsJson))
    }

    @Test
    fun `mergeReactionsJson does nothing for a message that isn't stored`() = runTest {
        dao.mergeReactionsJson(CHAT_HEX, 99, reactionsJson("👍" to 1))

        assertNull(dao.getReactionsJson(CHAT_HEX, 99))
    }

    /**
     * A stored emoji the incoming payload omits is treated as emptied on the server (mirrors iOS's
     * `applySummary`), not left stale: it tombstones to count 0 while keeping its version.
     */
    @Test
    fun `batch mergeReactionsJson merges every stored message and skips unstored ones`() = runTest {
        dao.upsert(text(1, "one"))
        dao.upsert(text(2, "two"))
        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 1))

        dao.mergeReactionsJson(
            CHAT_HEX,
            mapOf(
                1L to reactionsJson("👍" to 2),
                2L to reactionsJson("❤️" to 1),
                99L to reactionsJson("🔥" to 1),
            ),
        )

        assertEquals(mapOf("👍" to 2L), versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)))
        assertEquals(mapOf("❤️" to 1L), versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 2)))
        assertEquals(false, dao.exists(CHAT_HEX, 99))
    }

    @Test
    fun `mergeReactionsJson tombstones a stored emoji the incoming payload omits`() = runTest {
        dao.upsert(text(1, "hi"))
        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 1, "❤️" to 1))

        dao.mergeReactionsJson(CHAT_HEX, 1, reactionsJson("👍" to 2))

        assertEquals(
            mapOf("👍" to 2L, "❤️" to 1L),
            versionsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)),
        )
        assertEquals(
            mapOf("👍" to 2L, "❤️" to 0L),
            countsByEmoji(dao.getReactionsJson(CHAT_HEX, 1)),
        )
    }

    // endregion

    @Test
    fun `upsert of an unchanged row writes nothing`() = runTest {
        dao.upsert(text(1, "hi"))
        val before = totalChanges()

        dao.upsert(text(1, "hi"))

        assertEquals(before, totalChanges())
    }

    @Test
    fun `upsert of a changed row still writes it`() = runTest {
        dao.upsert(text(1, "hi"))
        val before = totalChanges()

        dao.upsert(text(1, "edited"))

        assertEquals(true, totalChanges() > before)
        assertEquals(listOf(MessageContentSerialized.Text("edited")), dao.getMessage(CHAT_HEX, 1)?.contentJson)
    }

    /** SQLite's running count of rows written on this connection, triggers included. */
    // region end-to-end encryption

    private val cipherJson = listOf(MessageContentSerialized.Encrypted(scheme = 1, nonce = "AA", ciphertext = "BB"))

    private fun encrypted(messageId: Long, state: EncryptionState) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = if (state == EncryptionState.DECRYPTED) listOf(MessageContentSerialized.Text("hi")) else cipherJson,
        timestampEpochMs = messageId * 1_000,
        unreadSeq = messageId,
        ciphertextJson = cipherJson,
        encryptionState = state,
    )

    @Test
    fun `a message waiting on its key is hidden from the preview`() = runTest {
        dao.upsert(listOf(text(1, "plain"), encrypted(2, EncryptionState.KEY_PENDING)))

        assertEquals(1L, dao.getLatestVisible(CHAT_HEX)?.messageId)
        assertEquals(listOf(1L), dao.getLatestVisibleForAllChats().map { it.messageId })
    }

    @Test
    fun `waiting messages are listed for reopening`() = runTest {
        dao.upsert(listOf(text(1, "plain"), encrypted(2, EncryptionState.KEY_PENDING)))

        assertEquals(true, dao.hasKeyPending(CHAT_HEX))
        assertEquals(listOf(2L), dao.getKeyPending(CHAT_HEX).map { it.messageId })
        assertEquals(listOf(CHAT_HEX), dao.chatsWithKeyPending())
    }

    @Test
    fun `the oldest encrypted message skips plaintext and waiting rows`() = runTest {
        dao.upsert(
            listOf(
                text(1, "plain"),
                encrypted(2, EncryptionState.KEY_PENDING),
                encrypted(3, EncryptionState.AUTH_FAILED),
                encrypted(4, EncryptionState.DECRYPTED),
            )
        )

        assertEquals(3L, dao.observeOldestEncrypted(CHAT_HEX).first())
    }

    @Test
    fun `confirming an encrypted send keeps its plaintext and gains the ciphertext`() = runTest {
        dao.upsert(pending("hello"))

        dao.confirmPendingMessage(
            CHAT_HEX,
            CLIENT_HEX,
            text(7, "hello").copy(ciphertextJson = cipherJson, encryptionState = EncryptionState.DECRYPTED),
        )

        val stored = dao.getMessage(CHAT_HEX, 7)!!
        assertEquals(listOf<MessageContentSerialized>(MessageContentSerialized.Text("hello")), stored.contentJson)
        assertEquals(cipherJson, stored.ciphertextJson)
        assertEquals(EncryptionState.DECRYPTED, stored.encryptionState)
    }

    // endregion

    private fun totalChanges(): Long =
        db.openHelper.writableDatabase.query("SELECT total_changes()").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private companion object {
        const val CHAT_HEX = "aabb"
        const val OTHER_HEX = "ccdd"
        const val SENDER_HEX = "1122"
        const val SELF_HEX = "3344"
        const val CLIENT_HEX = "eeff"
        const val SECOND_CLIENT_HEX = "ef01"
    }
}
