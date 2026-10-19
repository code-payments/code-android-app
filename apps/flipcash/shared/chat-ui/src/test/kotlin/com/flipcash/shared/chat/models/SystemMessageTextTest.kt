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
    )

    private val resources = mockk<ResourceHelper> {
        every { getString(any()) } answers { templates.getValue(firstArg()) }
        every { getString(any(), *anyVararg()) } answers {
            templates.getValue(firstArg()).format(*(args[1] as Array<*>))
        }
    }

    private fun text(
        joined: ID,
        admittedBy: ID? = null,
        nameOf: (ID) -> String? = names::get,
        fallback: String = "Someone joined",
    ) = MessageContent.System(fallback, UserJoined(joined, admittedBy))
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
}
