package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCardLinksTest {
    private val group = ChatId("aa")
    private val plain = "https://flipcash.com/chat/plain"
    private val invite = "https://flipcash.com/i/abc#s=secret"

    @Test
    fun `a card opened from an invite link encodes that link`() {
        val links = GroupCardLinks()
        links.put(group, GroupCardLinks.Link(invite, isInvite = true))

        val link = links.cardLink(group, plain)!!

        assertEquals(invite, link.url)
        assertTrue(link.isInvite)
    }

    @Test
    fun `a card with nothing handed over encodes the plain link`() {
        val link = GroupCardLinks().cardLink(group, plain)!!

        assertEquals(plain, link.url)
        assertFalse(link.isInvite)
    }

    @Test
    fun `the handed link is used once, so a card restored later falls back to the plain link`() {
        val links = GroupCardLinks()
        links.put(group, GroupCardLinks.Link(invite, isInvite = true))
        links.cardLink(group, plain)

        assertEquals(plain, links.cardLink(group, plain)!!.url)
    }

    @Test
    fun `a link handed for one group is not another group's`() {
        val links = GroupCardLinks()
        links.put(group, GroupCardLinks.Link(invite, isInvite = true))

        assertEquals(plain, links.cardLink(ChatId("bb"), plain)!!.url)
    }

    @Test
    fun `a link never prints its URL`() {
        assertFalse(invite in GroupCardLinks.Link(invite, isInvite = true).toString())
    }
}
