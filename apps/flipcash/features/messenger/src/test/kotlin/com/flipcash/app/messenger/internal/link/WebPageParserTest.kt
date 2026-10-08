package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The `pages` vectors of `link_metadata.json`. A failure is fixed in the canonical fixture, never here. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebPageParserTest {

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!
            .getResourceAsStream("link_metadata.json")!!
            .bufferedReader().use { it.readText() }
    )

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    @Test
    fun `pages match the cross-platform fixture`() {
        val rows = fixture().getJSONArray("pages")
        assertTrue(rows.length() > 0)
        val failures = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val name = row.getString("name")
            val expected = if (row.isNull("expect")) null else row.getJSONObject("expect").let {
                LinkCard.Web.State.Resolved(
                    title = it.getString("title"),
                    description = it.stringOrNull("description"),
                    imageUrl = it.stringOrNull("imageUrl"),
                    host = it.getString("host"),
                )
            }
            val actual = runCatching {
                WebPageParser.parse(row.getString("html").toByteArray(), row.getString("finalUrl"))
            }
            if (actual.isFailure) {
                failures += "$name: threw ${actual.exceptionOrNull()}"
            } else if (actual.getOrNull() != expected) {
                failures += "$name: expected $expected but was ${actual.getOrNull()}"
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${rows.length()} vectors failed:\n" + failures.joinToString("\n"))
    }
}
