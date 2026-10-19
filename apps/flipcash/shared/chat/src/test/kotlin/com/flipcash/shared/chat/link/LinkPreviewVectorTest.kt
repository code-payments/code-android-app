package com.flipcash.shared.chat.link

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.fail

/**
 * Runs `link_preview_send.json`: the whole-link rule, title cleaning and description cleaning a
 * sender applies before attaching a link preview. The fixture is a synced copy, so a disagreement
 * is fixed in the canonical file, never here. All failing vectors are reported together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkPreviewVectorTest {

    private val fixture: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("link_preview_send.json")!!
            .bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun check(section: String, run: (JSONObject) -> String?) {
        val failures = fixture.getJSONArray(section).objects().mapNotNull { v ->
            run(v)?.let { "${v.getString("name")}: $it" }
        }
        if (failures.isNotEmpty()) fail("$section vectors failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `limits match the fixture`() {
        val limits = fixture.getJSONObject("limits")
        kotlin.test.assertEquals(limits.getInt("url"), LinkPreviewChecks.URL_MAX)
        kotlin.test.assertEquals(limits.getInt("title"), LinkPreviewChecks.TITLE_MAX)
        kotlin.test.assertEquals(limits.getInt("description"), LinkPreviewChecks.DESCRIPTION_MAX)
    }

    @Test
    fun `whole link vectors`() = check("wholeLink") { v ->
        val got = LinkPreviewChecks.wholeLink(v.getString("text"), v.getString("url"))
        if (got != v.getBoolean("accepted")) "expected ${v.getBoolean("accepted")}, got $got" else null
    }

    @Test
    fun `title vectors`() = check("title") { v ->
        val expected = if (v.isNull("expected")) null else v.getString("expected")
        val got = LinkPreviewChecks.cleanTitle(v.getString("raw"))
        if (got != expected) "expected ${expected?.let { escape(it) }}, got ${got?.let { escape(it) }}" else null
    }

    @Test
    fun `description vectors`() = check("description") { v ->
        val expected = v.getString("expected")
        val got = LinkPreviewChecks.cleanDescription(v.getString("raw"))
        if (got != expected) "expected ${escape(expected)}, got ${escape(got)}" else null
    }

    private fun escape(s: String) = s.codePoints().toArray().joinToString("", "\"", "\"") {
        if (it in 0x20..0x7e) it.toChar().toString() else "\\u{%x}".format(it)
    }
}
