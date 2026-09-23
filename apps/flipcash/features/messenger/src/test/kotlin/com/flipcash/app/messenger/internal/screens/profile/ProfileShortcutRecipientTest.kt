package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When a profile offers Message and Send Cash. Kept in step with the iOS profile's rule. */
class ProfileShortcutRecipientTest {

    private val self = listOf(1.toByte())
    private val member = ChatParticipant.TipUser(
        userId = listOf(2.toByte()),
        profile = UserProfile.Empty.copy(username = "grace_hopper"),
    )

    @Test
    fun `a group member's profile offers the shortcuts`() {
        assertEquals(member, profileShortcutRecipient(member, ChatType.GROUP, self))
    }

    @Test
    fun `a tip DM's profile doesn't, because they would reopen the chat behind it`() {
        assertNull(profileShortcutRecipient(member, ChatType.TIP_DM, self))
    }

    @Test
    fun `the viewer's own profile doesn't`() {
        val me = member.copy(userId = self)
        assertNull(profileShortcutRecipient(me, ChatType.GROUP, self))
    }

    @Test
    fun `a contact or a missing participant doesn't`() {
        val contact = ChatParticipant.Contact(
            DeviceContact(
                e164 = "+15551234567",
                androidContactId = 1L,
                displayName = "Ada Lovelace",
                photoUri = null,
                displayNumber = "(555) 123-4567",
            )
        )
        assertNull(profileShortcutRecipient(contact, ChatType.GROUP, self))
        assertNull(profileShortcutRecipient(null, ChatType.GROUP, self))
    }
}
