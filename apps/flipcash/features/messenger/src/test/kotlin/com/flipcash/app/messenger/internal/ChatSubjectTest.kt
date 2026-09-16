package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The subject exists so that identity questions are exhaustive `when`s rather than chat-type
 * comparisons. These assert the three answers every renderer asks for, arm by arm, and that the
 * legacy contact arm still hands back the `ChatParticipant` the profile route needs.
 */
class ChatSubjectTest {

    private val contact = DeviceContact(
        e164 = "+15551234567",
        androidContactId = 1L,
        displayName = "Ada Lovelace",
        photoUri = null,
        displayNumber = "(555) 123-4567",
    )
    private val profile = UserProfile.Empty.copy(displayName = "Grace", username = "grace")
    private val userId = ByteArray(16) { 1 }.toList()
    private val chatId = ChatId(ByteArray(32) { 9 }.toList())

    @Test
    fun `a contact subject titles by the device contact and stays a participant`() {
        val subject = ChatSubject.Contact(ChatParticipant.Contact(contact))

        assertEquals("Ada Lovelace", subject.title)
        assertNull(subject.subtitle)
        assertEquals(ChatParticipant.Contact(contact), subject.asParticipant())
    }

    @Test
    fun `a tip subject titles by the profile and subtitles by the handle`() {
        val subject = ChatSubject.TipUser(ChatParticipant.TipUser(userId, profile))

        assertEquals("Grace", subject.title)
        assertEquals("@grace", subject.subtitle)
        assertEquals(ChatParticipant.TipUser(userId, profile), subject.asParticipant())
    }

    @Test
    fun `a group subject titles by the group and has no participant to open a profile for`() {
        val subject = ChatSubject.Group(
            chatId = chatId,
            groupTitle = "Flipcash Staff",
            picture = null,
            memberCount = 3L,
            rules = ChatRules(
                listener = listOf(
                    ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())
                ),
                speaker = emptyList(),
            ),
            isMember = true,
        )

        assertEquals("Flipcash Staff", subject.title)
        // The member count is a plural resource, so the chrome renders it off `memberCount`.
        assertNull(subject.subtitle)
        assertNull(subject.asParticipant())
        assertEquals(3L, subject.memberCount)
    }

    @Test
    fun `a group with no server title falls back rather than rendering blank`() {
        val subject = ChatSubject.Group(
            chatId = chatId,
            groupTitle = null,
            picture = null,
            memberCount = 0L,
            rules = null,
            isMember = false,
        )

        assertEquals("", subject.title)
        assertFalse(subject.isMember)
    }

    @Test
    fun `only a tip subject opens a profile`() {
        assertTrue(ChatSubject.TipUser(ChatParticipant.TipUser(userId, profile)).canViewProfile)
        assertFalse(ChatSubject.Contact(ChatParticipant.Contact(contact)).canViewProfile)
        assertFalse(
            ChatSubject.Group(chatId, "Flipcash Staff", null, 3L, null, true).canViewProfile
        )
    }
}
