package com.flipcash.shared.chat.ui

import com.flipcash.core.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    private fun groupSummary(
        content: List<MessageContent>,
        senderId: ID?,
        otherName: String = "Alice",
    ) = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(byteArrayOf(9)),
            type = ChatType.GROUP,
            members = listOf(
                ChatMember(userId = self, userProfile = profile("Me"), pointers = emptyList()),
                ChatMember(userId = other, userProfile = profile(otherName), pointers = emptyList()),
            ),
            lastMessage = ChatMessage(
                messageId = 42,
                senderId = senderId,
                content = content,
                timestamp = sentAt,
                unreadSeq = 0,
            ),
            lastActivity = sentAt,
            title = "Bad Boys",
        ),
        unreadCount = 0,
    )

    private fun groupReference(
        content: List<MessageContent>,
        senderId: ID? = other,
        otherName: String = "Alice",
        senderProfiles: Map<String, UserProfile> = emptyMap(),
    ) = groupSummary(content, senderId, otherName)
            .toConversationReference(
                selfId = self,
                tokensByMint = emptyMap(),
                resources = resources,
                senderProfiles = senderProfiles,
            )

    @Test
    fun `a group is named by its title`() {
        // Building the reference formats the preview eagerly, so these identity tests have to
        // stub it even though they do not assert on it.
        every {
            resources.getString(R.string.label_chat_preview_senderMessage, "Alice", "gm")
        } returns "Alice: gm"

        val reference = groupReference(listOf(MessageContent.Text("gm")))

        assertEquals("Bad Boys", reference.name)
        assertTrue(reference.isGroup)
    }

    @Test
    fun `a group's avatar authorizes off the chat's own profile`() {
        // Building the reference formats the preview eagerly, so these identity tests have to
        // stub it even though they do not assert on it.
        every {
            resources.getString(R.string.label_chat_preview_senderMessage, "Alice", "gm")
        } returns "Alice: gm"

        val reference = groupReference(listOf(MessageContent.Text("gm")))

        assertEquals(
            BlobAccessContext.ChatProfile(ChatId(byteArrayOf(9))),
            reference.avatarAccess,
        )
    }

    @Test
    fun `a DM's avatar still reads off the counterparty's profile`() {
        val reference = summary(listOf(MessageContent.Text("gm")), senderId = other)
            .toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources)

        assertEquals(BlobAccessContext.Profile(other), reference.avatarAccess)
    }

    @Test
    fun `another member's message is prefixed with their name`() {
        every {
            resources.getString(R.string.label_chat_preview_senderMessage, "Alice", "gm")
        } returns "Alice: gm"

        assertEquals("Alice: gm", groupReference(listOf(MessageContent.Text("gm"))).lastMessagePreview)
    }

    @Test
    fun `the viewer's own message keeps the You prefix in a group`() {
        every {
            resources.getString(R.string.label_chat_preview_sentMessage, "gm")
        } returns "You: gm"

        assertEquals(
            "You: gm",
            groupReference(listOf(MessageContent.Text("gm")), senderId = self).lastMessagePreview,
        )
    }

    @Test
    fun `a message from someone the roster subset omits is not prefixed`() {
        val stranger: ID = listOf(7)

        assertEquals(
            "gm",
            groupReference(listOf(MessageContent.Text("gm")), senderId = stranger).lastMessagePreview,
        )
    }

    @Test
    fun `a sender the roster subset omits is named from the resolved profiles`() {
        // The feed's group roster is often only the viewer, so this is the common case, not an edge.
        every {
            resources.getString(R.string.label_chat_preview_senderMessage, "Olive", "gm")
        } returns "Olive: gm"
        val stranger: ID = listOf(7)

        assertEquals(
            "Olive: gm",
            groupReference(
                listOf(MessageContent.Text("gm")),
                senderId = stranger,
                senderProfiles = mapOf(stranger.hexEncodedString() to profile("Olive")),
            ).lastMessagePreview,
        )
    }

    @Test
    fun `the roster's name wins over a resolved profile`() {
        every {
            resources.getString(R.string.label_chat_preview_senderMessage, "Alice", "gm")
        } returns "Alice: gm"

        assertEquals(
            "Alice: gm",
            groupReference(
                listOf(MessageContent.Text("gm")),
                senderProfiles = mapOf(other.hexEncodedString() to profile("Stale Alice")),
            ).lastMessagePreview,
        )
    }

    @Test
    fun `a group sender nothing can name is the one to ask for`() {
        val stranger: ID = listOf(7)

        assertEquals(
            stranger,
            groupSummary(listOf(MessageContent.Text("gm")), senderId = stranger)
                .unnamedGroupSender(self, emptyMap()),
        )
    }

    @Test
    fun `a group sender already named is not asked for`() {
        val stranger: ID = listOf(7)
        val summary = groupSummary(listOf(MessageContent.Text("gm")), senderId = stranger)

        assertNull(summary.unnamedGroupSender(self, mapOf(stranger.hexEncodedString() to profile("Olive"))))
        assertNull(groupSummary(listOf(MessageContent.Text("gm")), senderId = other).unnamedGroupSender(self, emptyMap()))
    }

    @Test
    fun `the viewer's own message and a DM are never asked for`() {
        assertNull(groupSummary(listOf(MessageContent.Text("gm")), senderId = self).unnamedGroupSender(self, emptyMap()))
        assertNull(
            summary(listOf(MessageContent.Text("gm")), senderId = listOf(7)).unnamedGroupSender(self, emptyMap()),
        )
    }

    @Test
    fun `a member cached without a profile is not prefixed with an empty name`() {
        // A member row with no profile row maps to UserProfile.Empty, whose name is "".
        assertEquals(
            "gm",
            groupReference(listOf(MessageContent.Text("gm")), otherName = "").lastMessagePreview,
        )
    }

    @Test
    fun `group cash from a member cached without a profile previews as the amount alone`() {
        val cash = MessageContent.Cash(
            intentId = listOf(3),
            amount = 25.toFiat(),
            mint = Mint.usdf,
        )

        assertEquals("$25.00", groupReference(listOf(cash), otherName = " ").lastMessagePreview)
    }

    @Test
    fun `a DM is not prefixed with the counterparty's name`() {
        assertEquals("gm", preview(listOf(MessageContent.Text("gm"))))
    }

    @Test
    fun `group cash from someone the roster subset omits previews as the amount alone`() {
        // "You received" would be a lie here: the cash went to the group, and the viewer may have
        // got none of it. The reserve mint leaves the token name off, so this formats no string —
        // which is why the mockk stub list stays empty.
        val stranger: ID = listOf(7)
        val cash = MessageContent.Cash(
            intentId = listOf(3),
            amount = 25.toFiat(),
            mint = Mint.usdf,
        )

        assertEquals("$25.00", groupReference(listOf(cash), senderId = stranger).lastMessagePreview)
    }

    @Test
    fun `a group nobody has spoken in yet reports no messages`() {
        val reference = ChatSummary(
            metadata = ChatMetadata(
                chatId = ChatId(byteArrayOf(9)),
                type = ChatType.GROUP,
                members = listOf(
                    ChatMember(userId = self, userProfile = profile("Me"), pointers = emptyList()),
                ),
                lastMessage = null,
                lastActivity = sentAt,
                title = "Bad Boys",
            ),
            unreadCount = 0,
        ).toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources)

        assertFalse(reference.hasMessages)
        assertNull(reference.lastMessagePreview)
    }

    @Test
    fun `a chat whose newest message has no preview still counts as having messages`() {
        // Media previews as nothing today, which is not the same as an empty chat — the row must
        // not label it "Nothing yet".
        val reference = groupReference(listOf(MessageContent.Media(items = emptyList(), caption = null)))

        assertTrue(reference.hasMessages)
        assertNull(reference.lastMessagePreview)
    }
}
