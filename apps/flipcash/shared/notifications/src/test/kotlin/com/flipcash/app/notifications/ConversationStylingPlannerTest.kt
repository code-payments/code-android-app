package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationStylingPlannerTest {

    // region Groups

    @Test
    fun `group push is a group conversation named by the stored row`() {
        val styling = planConversationStyling(
            payloadChatType = ChatType.GROUP,
            storedChatType = ChatType.GROUP,
            storedTitle = "Lunch crew",
        )

        assertTrue(styling.isGroupConversation)
        assertEquals("Lunch crew", styling.conversationTitle)
    }

    @Test
    fun `group this device has not stored is still a group conversation`() {
        // The first push from a group joined elsewhere arrives before its feed row does.
        val styling = planConversationStyling(
            payloadChatType = ChatType.GROUP,
            storedChatType = ChatType.UNKNOWN,
            storedTitle = null,
        )

        assertTrue(styling.isGroupConversation)
        assertNull(styling.conversationTitle)
    }

    @Test
    fun `a blank title is not a title`() {
        val styling = planConversationStyling(
            payloadChatType = ChatType.GROUP,
            storedChatType = ChatType.GROUP,
            storedTitle = "   ",
        )

        assertNull(styling.conversationTitle)
    }

    // endregion

    // region DMs

    @Test
    fun `dm push is not a group conversation`() {
        val styling = planConversationStyling(
            payloadChatType = ChatType.CONTACT_DM,
            storedChatType = ChatType.CONTACT_DM,
            storedTitle = null,
        )

        assertFalse(styling.isGroupConversation)
        assertNull(styling.conversationTitle)
    }

    @Test
    fun `a title stored against a dm is not used to name it`() {
        // A DM is named by its counterparty. A title on one is a row that shouldn't have it,
        // and honouring it would put a name on the notification the user cannot correct.
        val styling = planConversationStyling(
            payloadChatType = ChatType.TIP_DM,
            storedChatType = ChatType.TIP_DM,
            storedTitle = "Lunch crew",
        )

        assertFalse(styling.isGroupConversation)
        assertNull(styling.conversationTitle)
    }

    // endregion

    // region Payloads that don't say what the chat is

    @Test
    fun `push without chat metadata falls back to the stored type`() {
        val styling = planConversationStyling(
            payloadChatType = null,
            storedChatType = ChatType.GROUP,
            storedTitle = "Lunch crew",
        )

        assertEquals(ChatType.GROUP, styling.chatType)
        assertTrue(styling.isGroupConversation)
        assertEquals("Lunch crew", styling.conversationTitle)
    }

    @Test
    fun `an unknown type on the wire falls back to the stored type`() {
        val styling = planConversationStyling(
            payloadChatType = ChatType.UNKNOWN,
            storedChatType = ChatType.GROUP,
            storedTitle = null,
        )

        assertEquals(ChatType.GROUP, styling.chatType)
        assertTrue(styling.isGroupConversation)
    }

    @Test
    fun `neither source knowing the type renders as a one-to-one`() {
        val styling = planConversationStyling(
            payloadChatType = null,
            storedChatType = ChatType.UNKNOWN,
            storedTitle = null,
        )

        assertEquals(ChatType.UNKNOWN, styling.chatType)
        assertFalse(styling.isGroupConversation)
    }

    @Test
    fun `the payload wins over a stale stored type`() {
        val styling = planConversationStyling(
            payloadChatType = ChatType.GROUP,
            storedChatType = ChatType.TIP_DM,
            storedTitle = null,
        )

        assertEquals(ChatType.GROUP, styling.chatType)
    }

    // endregion
}
