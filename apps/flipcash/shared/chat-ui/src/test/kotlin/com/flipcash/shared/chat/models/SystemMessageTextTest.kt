package com.flipcash.shared.chat.models

import com.flipcash.core.R
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessageContent.SystemEvent.UserJoined
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a join notice names its users, and when it falls back to the server's text. */
class SystemMessageTextTest {

    private val self: ID = listOf(1)
    private val alex: ID = listOf(2)
    private val sam: ID = listOf(3)
    private val names = mapOf(alex to "Alex", sam to "Sam")

    private val templates = mapOf(
        R.string.label_chat_system_joined to "%1\$s joined the group",
        R.string.label_chat_system_joinedSelf to "You joined the group",
        R.string.label_chat_system_added to "%1\$s added %2\$s",
        R.string.label_chat_system_addedSelf to "%1\$s added you",
        R.string.label_chat_system_selfAdded to "You added %1\$s",
        R.string.label_chat_system_joinedViaInvite to "%1\$s joined with %2\$s’s invite link",
        R.string.label_chat_system_joinedSelfViaInvite to "You joined with %1\$s’s invite link",
        R.string.label_chat_system_joinedViaYourInvite to "%1\$s joined with your invite link",
    )

    private val resources = mockk<ResourceHelper> {
        every { getString(any<Int>()) } answers { templates.getValue(firstArg()) }
        every { getString(any<Int>(), *anyVararg()) } answers {
            templates.getValue(firstArg()).format(*(args[1] as Array<*>))
        }
    }

    private fun text(
        joined: ID,
        admittedBy: ID? = null,
        invitedBy: ID? = null,
        nameOf: (ID) -> String? = names::get,
        fallback: String = "Someone joined",
    ) = MessageContent.System(fallback, UserJoined(joined, admittedBy, invitedBy))
        .displayText(resources, self, nameOf)

    @Test
    fun `a direct join names who joined`() {
        assertEquals("Alex joined the group", text(alex))
    }

    @Test
    fun `the viewer joining reads as you`() {
        assertEquals("You joined the group", text(self))
    }

    @Test
    fun `an admitted join names both users`() {
        assertEquals("Sam added Alex", text(alex, admittedBy = sam))
    }

    @Test
    fun `the viewer admitted reads as you`() {
        assertEquals("Sam added you", text(self, admittedBy = sam))
    }

    @Test
    fun `the viewer admitting reads as you`() {
        assertEquals("You added Alex", text(alex, admittedBy = self))
    }

    @Test
    fun `admitting yourself is a plain join`() {
        assertEquals("Alex joined the group", text(alex, admittedBy = alex))
        assertEquals("You joined the group", text(self, admittedBy = self))
    }

    @Test
    fun `a name not known yet falls back to the server's text`() {
        assertEquals("Someone joined", text(alex, nameOf = { null }))
        assertEquals("Someone joined", text(alex, admittedBy = sam, nameOf = { if (it == sam) "Sam" else null }))
        assertEquals("Someone joined", text(alex, admittedBy = sam, nameOf = { if (it == alex) "Alex" else null }))
    }

    @Test
    fun `a notice with no event reads as its fallback text`() {
        assertEquals("Sam left", MessageContent.System("Sam left").displayText(resources, self) { null })
        assertNull(MessageContent.System("  ").displayText(resources, self) { null })
    }

    @Test
    fun `the users to look up leave out the viewer`() {
        assertEquals(listOf(alex, sam), MessageContent.System("x", UserJoined(alex, sam)).namedUsers(self))
        assertEquals(listOf(sam), MessageContent.System("x", UserJoined(self, sam)).namedUsers(self))
        assertEquals(listOf(alex), MessageContent.System("x", UserJoined(alex, alex)).namedUsers(self))
        assertEquals(emptyList(), MessageContent.System("x").namedUsers(self))
    }

    @Test
    fun `an invite-link join names the joiner and the link's owner`() {
        assertEquals("Alex joined with Sam’s invite link", text(alex, invitedBy = sam))
    }

    @Test
    fun `the viewer joining with an invite reads as you`() {
        assertEquals("You joined with Sam’s invite link", text(self, invitedBy = sam))
    }

    @Test
    fun `the viewer's own invite reads as your`() {
        assertEquals("Alex joined with your invite link", text(alex, invitedBy = self))
    }

    @Test
    fun `an invite join wins over an admitted one`() {
        assertEquals("Alex joined with Sam’s invite link", text(alex, admittedBy = sam, invitedBy = sam))
    }

    @Test
    fun `an invite join whose names are not known yet falls back`() {
        assertEquals("Someone joined", text(alex, invitedBy = sam, nameOf = { if (it == alex) "Alex" else null }))
        assertEquals("Someone joined", text(alex, invitedBy = sam, nameOf = { if (it == sam) "Sam" else null }))
        assertEquals("Someone joined", text(self, invitedBy = sam, nameOf = { null }))
    }

    @Test
    fun `an invite join names the inviter among the named users`() {
        val notice = MessageContent.System("x", UserJoined(alex, null, sam))
        assertEquals(listOf(alex, sam), notice.namedUsers(self))
        assertEquals(listOf(alex), notice.namedUsers(sam))
    }

    private fun linksOf(joined: ID, invitedBy: ID?, nameOf: (ID) -> String? = names::get): List<SystemUserLink> {
        val notice = MessageContent.System("x", UserJoined(joined, null, invitedBy))
        val text = notice.displayText(resources, self, nameOf).orEmpty()
        return notice.userLinks(resources, text, self, nameOf)
    }

    @Test
    fun `the inviter's name is the one tappable span`() {
        val text = "Alex joined with Sam’s invite link"
        val link = linksOf(alex, sam).single()
        assertEquals("Sam", text.substring(link.start, link.end))
        assertEquals(sam, link.userId)
    }

    @Test
    fun `the span lands on the inviter when the joiner's name contains it`() {
        val nameOf = { id: ID -> mapOf(alex to "Sam Jr", sam to "Sam")[id] }
        val link = linksOf(alex, sam, nameOf).single()
        assertEquals("Sam", "Sam Jr joined with Sam’s invite link".substring(link.start, link.end))
        assertEquals(19, link.start)
    }

    @Test
    fun `your own invite and a plain join have no tappable name`() {
        assertEquals(emptyList(), linksOf(alex, self))
        assertEquals(emptyList(), linksOf(self, null))
        assertEquals(emptyList(), linksOf(alex, null))
    }

    private fun spanText(joined: ID, invitedBy: ID?, nameOf: (ID) -> String?): Pair<String, String?> {
        val notice = MessageContent.System("x", UserJoined(joined, null, invitedBy))
        val text = notice.displayText(resources, self, nameOf).orEmpty()
        val link = notice.userLinks(resources, text, self, nameOf).singleOrNull()
        return text to link?.let { text.substring(it.start, it.end) }
    }

    @Test
    fun `the span lands on the inviter when the inviter's name contains the joiner's`() {
        val nameOf = { id: ID -> mapOf(alex to "Sam", sam to "Sam Jr")[id] }
        val (text, span) = spanText(alex, sam, nameOf)
        assertEquals("Sam joined with Sam Jr’s invite link", text)
        assertEquals("Sam Jr", span)
    }

    @Test
    fun `two people with the same name still get the inviter's span`() {
        val nameOf = { id: ID -> mapOf(alex to "Sam", sam to "Sam")[id] }
        val link = MessageContent.System("x", UserJoined(alex, null, sam)).let {
            it.userLinks(resources, "Sam joined with Sam’s invite link", self, nameOf).single()
        }
        assertEquals(16, link.start)
        assertEquals(sam, link.userId)
    }

    @Test
    fun `the viewer joining with a link whose owner's name is inside the wording still anchors`() {
        val nameOf = { id: ID -> mapOf(sam to "joined")[id] }
        val (text, span) = spanText(self, sam, nameOf)
        assertEquals("You joined with joined’s invite link", text)
        assertEquals("joined", span)
        assertEquals(16, MessageContent.System("x", UserJoined(self, null, sam))
            .userLinks(resources, text, self, nameOf).single().start)
    }

    @Test
    fun `an inviter who is the viewer has no link`() {
        assertEquals(emptyList(), MessageContent.System("x", UserJoined(alex, null, self))
            .userLinks(resources, "Alex joined with your invite link", self, names::get))
        assertEquals(emptyList(), MessageContent.System("x", UserJoined(self, null, self))
            .userLinks(resources, "You joined with your invite link", self, names::get))
    }

    @Test
    fun `a name that cannot be looked up has no link`() {
        val notice = MessageContent.System("x", UserJoined(alex, null, sam))
        assertEquals(emptyList(), notice.userLinks(resources, "Alex joined with Sam’s invite link", self) { null })
        assertEquals(
            emptyList(),
            notice.userLinks(resources, "Alex joined with Sam’s invite link", self) { if (it == sam) "Sam" else null },
        )
        assertEquals(
            emptyList(),
            notice.userLinks(resources, "Alex joined with Sam’s invite link", self) { if (it == alex) "Alex" else null },
        )
    }

    @Test
    fun `text that is not the formatted string has no link`() {
        val notice = MessageContent.System("x", UserJoined(alex, null, sam))
        assertEquals(emptyList(), notice.userLinks(resources, "Someone joined", self, names::get))
    }

    @Test
    fun `the span follows the translation's word order`() {
        val reordered = mockk<ResourceHelper> {
            every { getString(R.string.label_chat_system_joinedViaInvite, *anyVararg()) } answers {
                "%2\$s hat %1\$s per Einladungslink hinzugefügt".format(*(args[1] as Array<*>))
            }
        }
        val notice = MessageContent.System("x", UserJoined(alex, null, sam))
        val text = notice.displayText(reordered, self, names::get).orEmpty()
        assertEquals("Sam hat Alex per Einladungslink hinzugefügt", text)
        val link = notice.userLinks(reordered, text, self, names::get).single()
        assertEquals("Sam", text.substring(link.start, link.end))
    }
}
