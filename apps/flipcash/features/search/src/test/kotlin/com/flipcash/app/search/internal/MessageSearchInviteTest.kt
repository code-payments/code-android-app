package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.MessageContent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An invite link's fragment is the group's key, so Search neither matches on it nor shows it. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MessageSearchInviteTest {

    private val secret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val invite = "https://app.flipcash.com/chat/invite#s=$secret"

    private fun find(query: String, vararg messages: StoredMessage) =
        searchMessages(messages.toList(), SearchMatching.query(query)!!, inviteLinkLabel = "Invite link")

    @Test
    fun `a hit shows Invite link in place of the URL and keeps the text around it`() {
        val hits = find("join", stored(1, text("join us $invite tonight")))
        assertEquals(listOf("join us Invite link tonight"), hits.map { it.text })
    }

    @Test
    fun `a photo caption with an invite link is labelled too`() {
        val hits = find("photo", stored(1, MessageContent.Media(emptyList(), text("photo $invite"))))
        assertEquals(listOf("photo Invite link"), hits.map { it.text })
    }

    @Test
    fun `an invite link in a reply is labelled`() {
        val hits = find("here", stored(2, MessageContent.Reply(1, listOf(text("here $invite")))))
        assertEquals(listOf("here Invite link"), hits.map { it.text })
    }

    @Test
    fun `the secret is not searchable`() {
        assertTrue(find(secret.take(12), stored(1, text(invite))).isEmpty())
        assertTrue(find("chat/invite", stored(1, text(invite))).isEmpty())
    }

    @Test
    fun `a bad-secret invite URL is labelled`() {
        val hits = find("try", stored(1, text("try https://app.flipcash.com/chat/invite#s=not!a*valid~secret")))
        assertEquals(listOf("try Invite link"), hits.map { it.text })
    }

    @Test
    fun `a plain join link is left alone`() {
        val join = "https://app.flipcash.com/chat/3f2c1b9a-0d4e-4c8b-9a1f-2b3c4d5e6f70"
        val hits = find("join", stored(1, text("join $join")))
        assertEquals(listOf("join $join"), hits.map { it.text })
    }
}
