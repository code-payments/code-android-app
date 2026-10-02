package com.flipcash.shared.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `test-vectors/chat_archive.json`. The canonical copy lives in the orchestrator repo; this one is
 * synced. A failure here is a real regression or a cross-platform decision that has to be made in
 * the canonical fixture and re-synced to both platforms, never a local edit.
 *
 * Mention and reply detection stay native, so the fixture hands them in as inputs: these tests pin
 * the rules, not the parsers. `"unknown"` means the device could not tell, which both rules treat
 * as false.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatArchiveVectorTest {

    @Test
    fun `notify decisions match the cross-platform vectors`() {
        val cases = fixture().getJSONArray("notify").objects()
        // A fixture that failed to load reads as an empty list, and a loop over one passes.
        assertTrue(cases.isNotEmpty(), "chat_archive.json loaded no notify vectors")

        for (case in cases) {
            val push = case.getJSONObject("push")
            val expect = case.getJSONObject("expect")
            val name = case.getString("name")

            val actual = shouldNotify(
                archived = push.getBoolean("archived"),
                muted = push.getBoolean("muted"),
                mentionsViewer = push.triState("mentionsViewer"),
                repliesToViewer = push.triState("repliesToViewer"),
            )
            assertEquals(expect.getBoolean("notify"), actual, "$name: ${case.optString("note")}")
            // Rule 3: nothing a message does unarchives the chat. No Android code path can write
            // archive from a push, so the fixture's `expect.archived` is checked against the input.
            assertEquals(push.getBoolean("archived"), expect.getBoolean("archived"), "$name: a push never changes archive")
        }
    }

    @Test
    fun `list projections match the cross-platform vectors`() {
        val cases = fixture().getJSONArray("list").objects()
        assertTrue(cases.isNotEmpty(), "chat_archive.json loaded no list vectors")

        for (case in cases) {
            val name = case.getString("name")
            val note = case.optString("note")
            val expect = case.getJSONObject("expect")
            val entries = case.getJSONArray("chats").objects().map { chat ->
                ChatListEntry(
                    id = chat.getString("id"),
                    isGroup = chat.getString("type") == "group",
                    lastActivityMs = chat.getLong("lastActivity"),
                    archived = chat.getBoolean("archived"),
                    muted = chat.getBoolean("muted"),
                    hidden = chat.getBoolean("hidden"),
                    unread = chat.unread(),
                )
            }

            val actual = projectChatList(entries)

            assertEquals(expect.strings("main"), actual.main, "$name: main. $note")
            assertEquals(expect.strings("unreadChip"), actual.unreadChip, "$name: unreadChip. $note")
            assertEquals(expect.strings("groupsChip"), actual.groupsChip, "$name: groupsChip. $note")
            assertEquals(expect.strings("archived"), actual.archived, "$name: archived. $note")
            val counts = expect.getJSONObject("chipCounts")
            assertEquals(counts.getInt("unread"), actual.unreadChipCount, "$name: unread chip count. $note")
            assertEquals(counts.getInt("groups"), actual.groupsChipCount, "$name: groups chip count. $note")
            val row = expect.getJSONObject("archivedRow")
            assertEquals(row.getBoolean("visible"), actual.archivedRowVisible, "$name: archived row visible. $note")
            assertEquals(row.getInt("count"), actual.archivedRowCount, "$name: archived row count. $note")
            assertEquals(expect.getInt("tabBadge"), actual.tabBadge, "$name: tab badge. $note")
        }
    }

    @Test
    fun `a filter selects the ids its chip lists`() {
        val projection = projectChatList(
            listOf(
                ChatListEntry("a", isGroup = false, lastActivityMs = 3, archived = false, muted = false, hidden = false, unread = 0),
                ChatListEntry("b", isGroup = true, lastActivityMs = 2, archived = false, muted = false, hidden = false, unread = 4),
            )
        )
        assertEquals(listOf("a", "b"), projection.idsFor(ChatListFilter.All))
        assertEquals(listOf("b"), projection.idsFor(ChatListFilter.Unread))
        assertEquals(listOf("b"), projection.idsFor(ChatListFilter.Groups))
    }

    private fun JSONObject.triState(key: String): Boolean {
        val value = get(key)
        // true / false / "unknown": anything else is a typo in the fixture, not a case to guess at.
        assertTrue(value is Boolean || value == "unknown", "$key must be true, false or \"unknown\", was $value")
        return value == true
    }

    private fun JSONObject.unread(): Int? {
        val value = get("unread")
        assertTrue(value is Int || value == "unknown", "unread must be an int or \"unknown\", was $value")
        return value as? Int
    }

    private fun JSONObject.strings(key: String): List<String> =
        getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) } }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun fixture(): JSONObject {
        val json = javaClass.classLoader!!
            .getResourceAsStream("chat_archive.json")!!
            .bufferedReader().use { it.readText() }
        return JSONObject(json)
    }
}
