package com.flipcash.app.messenger.internal.link

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Host, cache key and limit tables of `link_metadata.json`. Synced copy -- a failure is fixed in the
 * canonical fixture and re-synced to both platforms, never edited here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebLinksTest {

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!
            .getResourceAsStream("link_metadata.json")!!
            .bufferedReader().use { it.readText() }
    )

    @Test
    fun `hosts match the cross-platform fixture`() {
        val rows = fixture().getJSONArray("hosts")
        assertEquals(true, rows.length() > 0)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val host = row.getString("host")
            assertEquals(row.getBoolean("eligible"), WebLinks.isEligibleHost(host), "host `$host`")
        }
    }

    @Test
    fun `cache keys match the cross-platform fixture`() {
        val rows = fixture().getJSONArray("cacheKeys")
        assertEquals(true, rows.length() > 0)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val url = row.getString("url")
            assertEquals(row.getString("key"), WebLinks.cacheKey(url), "url `$url`")
        }
    }

    @Test
    fun `limits match the cross-platform fixture`() {
        val limits = fixture().getJSONObject("limits")
        assertEquals(limits.getInt("maxBodyBytes"), WebLinks.MAX_BODY_BYTES)
        assertEquals(limits.getInt("maxImageBytes"), WebLinks.MAX_IMAGE_BYTES)
        assertEquals(limits.getInt("maxRedirects"), WebLinks.MAX_REDIRECTS)
        assertEquals(limits.getInt("maxConcurrent"), WebLinks.MAX_CONCURRENT)
        assertEquals(limits.getLong("timeoutSeconds"), WebLinks.TIMEOUT.inWholeSeconds)
        assertEquals(limits.getLong("resolvedTtlHours"), WebLinks.RESOLVED_TTL.inWholeHours)
        assertEquals(limits.getLong("emptyTtlHours"), WebLinks.EMPTY_TTL.inWholeHours)
    }

    @Test
    fun `a bracketed ipv6 url is refused through HttpUrl host`() {
        // HttpUrl drops the brackets; the colon check is what keeps the literal out.
        assertFalse(WebLinks.isEligibleHost("https://[::1]/".toHttpUrl().host))
        assertFalse(WebLinks.isEligibleHost("https://[2606:4700::1111]/".toHttpUrl().host))
        assertFalse(WebLinks.isEligibleHost("https://[::ffff:10.0.0.1]/".toHttpUrl().host))
    }
}
