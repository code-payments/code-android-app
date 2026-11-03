package com.flipcash.shared.chat.ui

import com.flipcash.shared.chat.testing.JSONObject
import com.flipcash.shared.chat.testing.readFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [isInviteLinkUrl] against `invite_link_url.json`: the cases `android.net.Uri.parse` used to answer. */
class InviteLinkUrlVectorTest {

    @Test
    fun `isInviteLinkUrl matches the vectors`() {
        val vectors = JSONObject(readFixture("invite_link_url.json")).getJSONArray("vectors")
        assertTrue(vectors.length() > 0, "no vectors loaded")
        val failures = (0 until vectors.length()).mapNotNull { i ->
            val v = vectors.getJSONObject(i)
            val url = v.getString("url")
            val expected = v.getBoolean("invite")
            val actual = isInviteLinkUrl(url)
            if (actual == expected) null else "${v.getString("name")}: `$url` expected $expected but was $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }
}
