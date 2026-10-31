package com.flipcash.shared.chat.media

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The send-plan and string halves of `chat_media.json` (git blob
 * 54f976f2737e37811af0d9bba5a849e7ba3f7ade). The canonical copy is the orchestrator repo's
 * `test-vectors/`; this one is synced, so a failure is a regression here or a decision to make in
 * the canonical fixture, never a local edit. The blob/encoding half is asserted in `:shared:blob`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatMediaVectorTest {

    private val fixture = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("chat_media.json")!!.bufferedReader().use { it.readText() }
    )

    @Test
    fun `the attachment ceiling matches the fixture`() {
        assertEquals(fixture.getInt("maxAttachments"), ChatMediaSendPlan.MAX_ATTACHMENTS)
    }

    @Test
    fun `fan-out matches the vectors`() {
        val cases = fixture.getJSONArray("fanOut")
        assertTrue(cases.length() > 0, "no fanOut vectors loaded")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            val chips = case.getJSONArray("chips").let { a -> (0 until a.length()).map { a.getString(it) } }
            val plan = ChatMediaSendPlan.build(
                chips = chips,
                text = case.getString("text"),
                replyTo = case.optNullableString("replyTo")?.id(),
            )

            val expected = case.getJSONArray("messages")
            assertEquals(expected.length(), plan.size, "$name: message count")
            for (m in 0 until expected.length()) {
                val want = expected.getJSONObject(m)
                val got = plan[m]
                val replyTo = want.optNullableString("replyTo")?.id()
                when (want.getString("kind")) {
                    "text" -> assertEquals(
                        ChatMediaSendPlan.Message.Text(want.getString("text"), replyTo), got, "$name[$m]",
                    )
                    "media" -> assertEquals(
                        ChatMediaSendPlan.Message.Media(want.getString("chip"), want.optNullableString("caption"), replyTo),
                        got,
                        "$name[$m]",
                    )
                    else -> error("$name[$m]: unknown kind")
                }
            }
        }
    }

    @Test
    fun `strings match the vectors`() {
        val cases = fixture.getJSONArray("strings")
        assertTrue(cases.length() > 0, "no strings vectors loaded")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val caption = case.optNullableString("caption")
            val expected = case.getJSONObject("expected")
            assertEquals(expected.getString("snippet"), ChatMediaText.snippet(caption, "Photo"), case.getString("name"))
            assertEquals(expected.getString("preview"), ChatMediaText.preview(caption, "Photo"), case.getString("name"))
        }
    }

    @Test
    fun `a push carries the caption or the bare photo`() {
        assertEquals("from today", ChatMediaText.pushText("from today"))
        assertEquals("📷 Photo", ChatMediaText.pushText(null))
        assertEquals("📷 Photo", ChatMediaText.pushText("  "))
    }

    // The fixture names a cited message "m1"; the plan only needs it to be some id.
    private fun String.id(): Long = removePrefix("m").toLong()

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else getString(key)
}
