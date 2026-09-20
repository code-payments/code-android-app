package com.flipcash.shared.chat

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.persistence.sources.ChatDraftDataSource
import com.flipcash.app.persistence.sources.ChatDraftRecord
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.internal.RealChatDraftStore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `test-vectors/chat_draft.json`. The canonical copy lives in the orchestrator repo; this one is
 * synced. A failure here is either a real regression or a cross-platform decision that has to be
 * made in the canonical fixture and re-synced to both platforms — never a local edit.
 *
 * The vectors are composer actions and the draft they must leave behind, so the replay below is
 * the composer's own state machine: what the ViewModel does to `chatInputState`, `replyingTo` and
 * the edit stash, minus the Compose types. [chatDraftOf] is the shared piece — the rule about what
 * an in-progress edit contributes lives there rather than in the ViewModel, so this suite and the
 * app read it from the same place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatDraftVectorTest {

    private val chatId = ChatId(hex = "a1b2c3d4")

    @Test
    fun `drafts match the cross-platform vectors`() = runTest {
        for (vector in vectors()) {
            val name = vector.getString("name")
            val note = vector.getString("note")
            val store = storeOverOneRow()
            val composer = Composer()

            val actions = vector.getJSONArray("actions")
            for (i in 0 until actions.length()) {
                composer.apply(actions.getJSONObject(i), chatId, store)
            }

            val actual = store.load(chatId)
            val expected = vector.optJSONObject("draft")

            if (expected == null) {
                assertNull(actual, "vector `$name`: $note")
                continue
            }

            assertNotNull(actual, "vector `$name`: $note")
            assertEquals(expected.getString("text"), actual.text, "vector `$name`: $note")

            val expectedReply = expected.optJSONObject("replyTarget")
            if (expectedReply == null) {
                assertNull(actual.replyTarget, "vector `$name`: $note")
            } else {
                val reply = assertNotNull(actual.replyTarget, "vector `$name`: $note")
                assertEquals(
                    expectedReply.getString("messageId"),
                    messageIds.nameOf(reply.messageId),
                    "vector `$name`: $note",
                )
                assertEquals(expectedReply.getString("author"), reply.authorName, "vector `$name`: $note")
                assertEquals(
                    ChatDraftSnippet.Text(expectedReply.getString("snippet")),
                    reply.snippet,
                    "vector `$name`: $note",
                )
            }
        }
    }

    /**
     * The composer as the ViewModel keeps it: absolute text, an aimed reply, and the new-message
     * draft an edit displaced. The reducers this mirrors are `Event.ReplyToMessage`,
     * `Event.EditMessage`, `Event.SendMessage` and `finishEditing`.
     */
    private class Composer {
        private var text: String = ""
        private var replyTarget: ChatDraftReply? = null
        private var editStash: String? = null

        suspend fun apply(action: JSONObject, chatId: ChatId, store: ChatDraftStore) {
            when (val op = action.getString("op")) {
                "type" -> text = action.getString("text")

                // Aiming a reply leaves any edit, which is how the ReplyToMessage reducer works.
                "reply" -> {
                    replyTarget = action.optJSONObject("target")?.toDraftReply()
                    editStash = null
                }

                // Entering an edit stashes the new-message draft once — a second edit without
                // leaving the first keeps the original stash — and drops the reply strip.
                "edit" -> {
                    editStash = editStash ?: text
                    text = action.getString("text")
                    replyTarget = null
                }

                // Cancelling puts the stashed draft back, so it is an ordinary draft again.
                "cancelEdit" -> {
                    text = editStash.orEmpty()
                    editStash = null
                }

                // The field clears and the draft goes with it, at that moment rather than on
                // delivery.
                "send" -> {
                    text = ""
                    replyTarget = null
                    store.save(chatId, ChatDraftSnapshot.Empty)
                }

                "leave" -> store.save(chatId, chatDraftOf(text, replyTarget, editStash))

                else -> error("unknown op `$op`")
            }
        }
    }

    /** A [ChatDraftDataSource] backed by a single nullable row, which is all one chat needs. */
    private fun storeOverOneRow(): ChatDraftStore {
        var row: ChatDraftRecord? = null
        val dataSource = mockk<ChatDraftDataSource>()
        val record = slot<ChatDraftRecord>()
        coEvery { dataSource.get(any()) } answers { row }
        coEvery { dataSource.upsert(capture(record)) } answers { row = record.captured }
        coEvery { dataSource.delete(any()) } answers { row = null }
        coEvery { dataSource.clear() } answers { row = null }
        return RealChatDraftStore(dataSource, TestDispatchers(TestCoroutineScheduler())) { 0L }
    }

    private fun vectors(): List<JSONObject> {
        val json = javaClass.classLoader!!
            .getResourceAsStream("chat_draft.json")!!
            .bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("vectors")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    companion object {
        /**
         * The fixture identifies a cited message by UUID; Android's message ids are longs. The id
         * is opaque to every rule being pinned here, so it is carried through the round trip
         * rather than reinterpreted — what the vectors assert is that the same message comes back.
         */
        private val messageIds = object {
            private val assigned = mutableMapOf<String, Long>()
            fun idOf(name: String): Long = assigned.getOrPut(name) { assigned.size + 1L }
            fun nameOf(id: Long): String? = assigned.entries.firstOrNull { it.value == id }?.key
        }

        private fun JSONObject.toDraftReply() = ChatDraftReply(
            messageId = messageIds.idOf(getString("messageId")),
            authorName = getString("author"),
            snippet = ChatDraftSnippet.Text(getString("snippet")),
        )
    }
}
