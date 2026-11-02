package com.flipcash.shared.chat.ui

import com.flipcash.core.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The chat-list row never shows a raw invite URL: its fragment is the group's key. The row reads
 * "Invite link" in its place and keeps the rest of the message and its sender prefix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatSummaryInvitePreviewTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)
    private val self: ID = listOf(1)
    private val other: ID = listOf(2)
    private val invite = "https://app.flipcash.com/chat/invite#s=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    private val resources = mockk<ResourceHelper> {
        every { getString(R.string.label_inviteLinkPreview) } returns "Invite link"
        every { getString(R.string.label_chat_preview_senderMessage, *anyVararg()) } answers {
            val (name, message) = (args[1] as Array<*>)
            "$name: $message"
        }
        every { getString(R.string.label_chat_preview_sentMessage, *anyVararg()) } answers {
            "You: ${(args[1] as Array<*>)[0]}"
        }
    }

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun preview(text: String, senderId: ID = other): String? = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(byteArrayOf(9)),
            type = ChatType.GROUP,
            members = listOf(
                ChatMember(userId = self, userProfile = profile("Me"), pointers = emptyList()),
                ChatMember(userId = other, userProfile = profile("XCTests"), pointers = emptyList()),
            ),
            lastMessage = ChatMessage(
                messageId = 42,
                senderId = senderId,
                content = listOf(MessageContent.Text(text)),
                timestamp = sentAt,
                unreadSeq = 0,
            ),
            lastActivity = sentAt,
            title = "Links",
        ),
        unreadCount = 0,
    ).toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources).lastMessagePreview

    @Test
    fun `a bare invite URL previews as Invite link under the sender's name`() {
        assertEquals("XCTests: Invite link", preview(invite))
    }

    @Test
    fun `text around an invite URL stays`() {
        assertEquals("XCTests: come join Invite link today", preview("come join $invite today"))
    }

    @Test
    fun `an invite URL with a bad secret still previews as Invite link`() {
        assertEquals("You: Invite link", preview("https://app.flipcash.com/chat/invite#s=bad!secret", senderId = self))
    }

    @Test
    fun `a plain join link previews unchanged`() {
        val join = "https://app.flipcash.com/chat/3f2c1b9a-0d4e-4c8b-9a1f-2b3c4d5e6f70"
        assertEquals("XCTests: $join", preview(join))
    }
}
