package com.flipcash.app.notifications

import com.flipcash.app.core.util.Linkify
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.bytes
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatTapTargetPlannerTest {

    private val groupUuid = UUID.fromString("0189d5f1-3c6a-7b4e-8f21-9c3d4e5f6a7b")
    private val groupChatId = ChatId(groupUuid.bytes)
    private val dmChatId = ChatId(ByteArray(32) { it.toByte() })

    // region Chats the app can open

    @Test
    fun `a group push opens the conversation through its invite link`() {
        // The link an invite carries, so a push tap and an invite tap are one entry, not two that
        // have to agree about where an unjoined group lands.
        val target = planChatTapTarget(ChatType.GROUP, groupChatId)
        assertIs<ChatTapTarget.Conversation>(target)
        assertEquals("https://app.flipcash.com/chat/$groupUuid", target.link)
        assertEquals(Linkify.groupChatInvite(groupChatId), target.link)
    }

    @Test
    fun `a group whose id has no uuid form still reaches the chat screen`() {
        // Should not happen — a group id is a 16-byte UUID — but the older link resolves to the
        // same screen, so the tap degrades to it rather than to the camera.
        val target = planChatTapTarget(ChatType.GROUP, dmChatId)
        assertIs<ChatTapTarget.Conversation>(target)
        assertEquals(Linkify.tipChatById(dmChatId), target.link)
    }

    @Test
    fun `a tip dm push opens the conversation`() {
        val target = planChatTapTarget(ChatType.TIP_DM, dmChatId)
        assertIs<ChatTapTarget.Conversation>(target)
        assertEquals(Linkify.tipChatById(dmChatId), target.link)
    }

    // endregion

    // region Chats the app has no screen for

    @Test
    fun `a contact dm push launches the app`() {
        // The Send tab it used to open was removed, and AppRouter routes nothing for it.
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(ChatType.CONTACT_DM, dmChatId))
    }

    @Test
    fun `an unresolved type launches the app`() {
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(ChatType.UNKNOWN, dmChatId))
        assertEquals(ChatTapTarget.AppLauncher, planChatTapTarget(null, dmChatId))
    }

    // endregion
}
