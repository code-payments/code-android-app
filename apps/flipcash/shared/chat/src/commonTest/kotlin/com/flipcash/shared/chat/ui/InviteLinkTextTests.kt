package com.flipcash.shared.chat.ui

import com.flipcash.shared.chat.testing.JSONObject
import com.flipcash.shared.chat.testing.readFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** An invite link's fragment is the group's key, so a preview line shows "Invite link" in its place. */
abstract class InviteLinkTextTests {

    private val invite = "https://app.flipcash.com/chat/invite#s=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    private fun label(text: String) = labelInviteLinks(text, "Invite link")

    @Test
    fun `a bare invite URL becomes the label`() {
        assertEquals("Invite link", label(invite))
    }

    @Test
    fun `text around an invite URL is kept`() {
        assertEquals("join us: Invite link see you there", label("join us: $invite see you there"))
    }

    @Test
    fun `an invite URL with a bad secret is labelled and none of its fragment shows`() {
        val result = label("try https://app.flipcash.com/chat/invite#s=not!a*valid~secret now")
        assertEquals("try Invite link now", result)
        assertFalse(result.contains("#s="))
    }

    @Test
    fun `an invite URL with no fragment is still an invite`() {
        assertEquals("Invite link", label("https://app.flipcash.com/chat/invite"))
    }

    @Test
    fun `a plain join link is left alone`() {
        val join = "https://app.flipcash.com/chat/3f2c1b9a-0d4e-4c8b-9a1f-2b3c4d5e6f70"
        assertEquals("ask here $join", label("ask here $join"))
    }

    @Test
    fun `every invite in a message is labelled`() {
        assertEquals("Invite link and Invite link", label("$invite and $invite"))
    }

    @Test
    fun `a jump link wrapping an invite is an invite`() {
        val jump = "https://jump.flipcash.com/#source=" + java.net.URLEncoder.encode(invite, "UTF-8")
        assertTrue(isInviteLinkUrl(jump))
        assertEquals("Invite link", label(jump))
    }

    @Test
    fun `invite path on another host is not ours`() {
        val other = "https://example.com/chat/invite#s=abc"
        assertFalse(isInviteLinkUrl(other))
        assertEquals(other, label(other))
    }

    @Test
    fun `the label is only asked for when there is an invite`() {
        var asked = false
        assertEquals("gm", labelInviteLinks("gm") { asked = true; "Invite link" })
        assertFalse(asked)
    }

    @Test
    fun `the label replaces the vectors' invite links inside a sentence`() {
        val vectors = JSONObject(readFixture("invite_link_url.json")).getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val url = v.getString("url")
            // labelInviteLinks skips text that doesn't spell the invite path out, and detectUrls
            // needs a web scheme; the other shapes are covered by the vector test above.
            if (!v.getBoolean("invite") || !url.startsWith("http", ignoreCase = true)) continue
            if (!url.contains("chat/invite", ignoreCase = true) && !url.contains("chat%2Finvite", ignoreCase = true)) continue
            val labelled = label("see $url now")
            assertEquals("see Invite link now", labelled, v.getString("name"))
        }
    }
}
