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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The conversation row previews the last message. A reply is a wrapper around the body the sender
 * typed, so the row has to look through it — otherwise a chat whose newest message is a reply
 * shows no preview at all.
 */
class ChatSummaryPreviewTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)
    private val self: ID = listOf(1)
    private val other: ID = listOf(2)

    private val resources = mockk<ResourceHelper>()

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    private fun summary(
        content: List<MessageContent>,
        senderId: ID?,
    ) = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(byteArrayOf(9)),
            type = ChatType.TIP_DM,
            members = listOf(
                ChatMember(userId = self, userProfile = profile("Me"), pointers = emptyList()),
                ChatMember(userId = other, userProfile = profile("Them"), pointers = emptyList()),
            ),
            lastMessage = ChatMessage(
                messageId = 42,
                senderId = senderId,
                content = content,
                timestamp = sentAt,
                unreadSeq = 0,
            ),
            lastActivity = sentAt,
        ),
        unreadCount = 0,
    )

    private fun preview(content: List<MessageContent>, senderId: ID? = other): String? =
        summary(content, senderId)
            .toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources)
            .lastMessagePreview

    @Test
    fun `a reply previews as the body the sender typed`() {
        val reply = MessageContent.Reply(
            repliedMessageId = 7,
            content = listOf(MessageContent.Text("on my way")),
        )

        assertEquals("on my way", preview(listOf(reply)))
    }

    @Test
    fun `a reply the user sent is prefixed once, not once per layer`() {
        every {
            resources.getString(R.string.label_chat_preview_sentMessage, "on my way")
        } returns "You: on my way"

        val reply = MessageContent.Reply(
            repliedMessageId = 7,
            content = listOf(MessageContent.Text("on my way")),
        )

        assertEquals("You: on my way", preview(listOf(reply), senderId = self))
    }

    @Test
    fun `a reply carrying no body has nothing to preview`() {
        val reply = MessageContent.Reply(repliedMessageId = 7, content = emptyList())

        assertNull(preview(listOf(reply)))
    }

    @Test
    fun `a reply nested past the unwrap bound gives up rather than looping`() {
        // Nothing the app sends looks like this; it stands in for malformed content off the wire.
        var nested: MessageContent = MessageContent.Text("buried")
        repeat(6) { nested = MessageContent.Reply(repliedMessageId = 7, content = listOf(nested)) }

        assertNull(preview(listOf(nested)))
    }
}
