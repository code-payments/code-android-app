package com.flipcash.shared.chat.media

import com.flipcash.shared.chat.testing.JSONObject
import com.flipcash.shared.chat.testing.readFixture
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * `chat_media_groups.json`, synced byte for byte from the orchestrator's
 * `docs/specs/assets/chat-media/`. A failure is a regression here or a decision to make in the
 * canonical fixture, never a local edit.
 */
class ChatMediaGroupsVectorTest {

    private val fixture = JSONObject(readFixture("chat_media_groups.json"))

    @Test
    fun `the window matches the fixture`() {
        val seconds = fixture.getString("windowSeconds").toDouble()
        assertEquals((seconds * 1000).roundToLong().milliseconds, ChatMediaGroups.WINDOW)
    }

    @Test
    fun `stacks match every case`() {
        val cases = fixture.getJSONArray("cases")
        assertEquals(12, cases.length(), "case count")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            val messages = case.getJSONArray("messages").let { a ->
                (0 until a.length()).map { m ->
                    val message = a.getJSONObject(m)
                    ChatMediaGroups.Candidate(
                        messageId = message.getLong("seq"),
                        senderKey = message.getString("sender"),
                        isPhoto = message.getString("kind") == "photo",
                        timestamp = Instant.fromEpochMilliseconds(
                            (message.getString("ts").toDouble() * 1000).roundToLong(),
                        ),
                        hasCaption = message.getBoolean("hasCaption"),
                        isReply = message.getBoolean("isReply"),
                    )
                }
            }
            val expected = case.getJSONArray("stacks").let { a ->
                (0 until a.length()).map { s ->
                    a.getJSONArray(s).let { ids -> (0 until ids.length()).map { ids.getInt(it).toLong() } }
                }
            }
            assertEquals(expected, ChatMediaGroups.stacks(messages), name)
        }
    }
}
