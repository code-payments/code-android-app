package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTapTargetPlannerTest {

    // region Chats the app can open

    @Test
    fun `a group push opens the conversation`() {
        assertEquals(ChatTapTarget.Conversation, planChatTapTarget(ChatType.GROUP))
    }

    @Test
    fun `a tip dm push opens the conversation`() {
        assertEquals(ChatTapTarget.Conversation, planChatTapTarget(ChatType.TIP_DM))
    }

    // endregion

    // region Chats the app has no screen for

    @Test
    fun `a contact dm push launches the app`() {
        // The Send tab it used to open was removed, and AppRouter routes nothing for it.
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(ChatType.CONTACT_DM))
    }

    @Test
    fun `an unresolved type launches the app`() {
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(ChatType.UNKNOWN))
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(null))
    }

    // endregion
}
