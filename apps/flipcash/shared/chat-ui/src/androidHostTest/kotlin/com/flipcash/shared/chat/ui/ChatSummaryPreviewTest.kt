package com.flipcash.shared.chat.ui

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_chat_media_photo
import com.flipcash.core.resources.label_chat_preview_receivedCash
import com.flipcash.core.resources.label_chat_preview_senderMessage
import com.flipcash.core.resources.label_chat_preview_sentMessage
import com.flipcash.core.resources.label_chat_preview_sentRequest
import com.flipcash.core.resources.label_chat_preview_sentRequest_bySender
import com.flipcash.core.resources.label_chat_preview_sentRequest_untitled
import com.flipcash.core.resources.label_chat_preview_sentRequest_untitled_bySender
import com.flipcash.core.resources.label_chat_preview_sharedProfile
import com.flipcash.core.resources.label_chat_system_joined
import com.flipcash.core.resources.label_chat_system_joinedSelfViaInvite
import com.flipcash.core.resources.label_chat_system_joinedViaInvite
import com.flipcash.core.resources.label_chat_system_joinedViaYourInvite
import org.jetbrains.compose.resources.StringResource
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
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
            resources.getString(Res.string.label_chat_preview_sentMessage, "on my way")
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
            resources.getString(Res.string.label_chat_preview_senderMessage, "Alice", "gm")
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
            resources.getString(Res.string.label_chat_preview_senderMessage, "Alice", "gm")
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
            resources.getString(Res.string.label_chat_preview_senderMessage, "Alice", "gm")
        } returns "Alice: gm"

        assertEquals("Alice: gm", groupReference(listOf(MessageContent.Text("gm"))).lastMessagePreview)
    }

    @Test
    fun `the viewer's own message keeps the You prefix in a group`() {
        every {
            resources.getString(Res.string.label_chat_preview_sentMessage, "gm")
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
            resources.getString(Res.string.label_chat_preview_senderMessage, "Olive", "gm")
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
            resources.getString(Res.string.label_chat_preview_senderMessage, "Alice", "gm")
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
    fun `cash previews in the viewer's currency, from the USD it settled at`() {
        // 7,500 ARS that settled as $5 reads as $6.85 to a viewer on CAD at 1.37, whatever the
        // peso has done since — and with no flag or second line in the preview.
        val cad = Rate(fx = 1.37, currency = CurrencyCode.CAD)
        val exchange = mockk<Exchange> {
            every { preferredRate } returns cad
            every { rates() } returns mapOf(CurrencyCode.CAD to cad)
        }
        val cash = MessageContent.Cash(
            intentId = listOf(3),
            amount = Fiat(7_500.0, CurrencyCode.ARS),
            mint = Mint.usdf,
            tokenQuarks = Fiat(5.0, CurrencyCode.USD).quarks,
        )
        val expected = Fiat(6.85, CurrencyCode.CAD).formatted()
        every { resources.getString(Res.string.label_chat_preview_receivedCash, expected) } returns "Received $expected"

        val reference = summary(listOf(cash), senderId = other)
            .toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources, exchange = exchange)

        assertEquals("Received $expected", reference.lastMessagePreview)
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

    private fun awaitsInvites(
        type: ChatType = ChatType.GROUP,
        memberCount: Long = 1,
        canInvite: Boolean = true,
        isPrivate: Boolean = true,
        lastMessage: ChatMessage? = null,
    ) = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(byteArrayOf(9)),
            type = type,
            members = listOf(ChatMember(userId = self, userProfile = profile("Me"), pointers = emptyList())),
            lastMessage = lastMessage,
            lastActivity = sentAt,
            title = "Bad Boys",
            rosterSummary = RosterSummary(memberCount = memberCount, version = 1),
            viewerState = ViewerState(permissions = ViewerState.Permissions(canInvite = canInvite)),
            isPrivate = isPrivate,
        ),
        unreadCount = 0,
    ).toConversationReference(selfId = self, tokensByMint = emptyMap(), resources = resources).awaitsInvites

    @Test
    fun `a lone host of a private group awaits invites`() = assertTrue(awaitsInvites())

    @Test
    fun `a public group's creator awaits invites without can_invite`() =
        assertTrue(awaitsInvites(canInvite = false, isPrivate = false))

    @Test
    fun `a private group the viewer cannot invite to does not await invites`() =
        assertFalse(awaitsInvites(canInvite = false, isPrivate = true))

    @Test
    fun `a group with anyone else in it does not await invites`() {
        assertFalse(awaitsInvites(memberCount = 2))
        // Never fetched: the default count is 0, which is not "just me".
        assertFalse(awaitsInvites(memberCount = 0))
    }

    @Test
    fun `a direct chat never awaits invites`() = assertFalse(awaitsInvites(type = ChatType.CONTACT_DM))

    @Test
    fun `a photo previews as its caption behind the camera`() {
        every { resources.getString(Res.string.label_chat_media_photo) } returns "Photo"
        val bare = MessageContent.Media(items = emptyList(), caption = null)
        val captioned = MessageContent.Media(items = emptyList(), caption = MessageContent.Text("sunset"))

        assertEquals("📷 Photo", preview(listOf(bare)))
        assertEquals("📷 sunset", preview(listOf(captioned)))
    }

    @Test
    fun `a photo is prefixed like a typed message`() {
        every { resources.getString(Res.string.label_chat_media_photo) } returns "Photo"
        every { resources.getString(Res.string.label_chat_preview_sentMessage, "📷 Photo") } returns "You: 📷 Photo"
        val bare = MessageContent.Media(items = emptyList(), caption = null)

        every {
            resources.getString(Res.string.label_chat_preview_senderMessage, "Alice", "📷 Photo")
        } returns "Alice: 📷 Photo"

        assertEquals("You: 📷 Photo", preview(listOf(bare), senderId = self))
        val group = groupReference(listOf(bare))
        assertTrue(group.hasMessages)
        assertEquals("Alice: 📷 Photo", group.lastMessagePreview)
    }

    @Test
    fun `a shared profile previews the same whoever the sender is`() {
        every {
            resources.getString(Res.string.label_chat_preview_sharedProfile)
        } returns "Shared a profile"
        val widget = MessageContent.Widget(WidgetContent.ShareProfile("brad"))

        assertEquals("Shared a profile", preview(listOf(widget)))
        assertEquals("Shared a profile", preview(listOf(widget), senderId = self))
    }

    private val requestId: ID = List(32) { 7 }

    private fun request(share: Fiat) = PaymentRequest(
        id = requestId,
        creator = other,
        createdAt = sentAt,
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(byteArrayOf(9)),
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(100_000),
                shares = listOf(self, other).map { Share(userId = it, amount = share, paid = null) },
            ),
        ),
    )

    @Test
    fun `a payment request previews as who sent it and its title`() {
        every {
            resources.getString(Res.string.label_chat_preview_sentRequest_bySender, "Alice", "Dinner")
        } returns "Alice sent request for Dinner"
        every { resources.getString(Res.string.label_chat_preview_sentRequest, "Dinner") } returns "You sent request for Dinner"
        val widget = listOf(MessageContent.Widget(WidgetContent.PaymentRequest(requestId)))
        val requests = mapOf(requestId to request(Fiat(20.0, CurrencyCode.USD)))

        fun groupPreview(senderId: ID) = groupSummary(widget, senderId, "Alice")
            .toConversationReference(self, emptyMap(), resources, paymentRequests = requests)
            .lastMessagePreview

        assertEquals("Alice sent request for Dinner", groupPreview(other))
        assertEquals("You sent request for Dinner", groupPreview(self))
    }

    @Test
    fun `a payment request whose title can't be opened previews without it`() {
        every {
            resources.getString(Res.string.label_chat_preview_sentRequest_untitled_bySender, "Alice")
        } returns "Alice sent request"
        every { resources.getString(Res.string.label_chat_preview_sentRequest_untitled) } returns "You sent request"
        val widget = listOf(MessageContent.Widget(WidgetContent.PaymentRequest(requestId)))
        val held = request(Fiat(20.0, CurrencyCode.USD))
        val requests = mapOf(
            requestId to held.copy(kind = (held.kind as PaymentRequest.Kind.GroupSplit).let { kind ->
                kind.copy(split = kind.split.copy(text = PaymentRequestText.Undecryptable))
            }),
        )

        fun groupPreview(senderId: ID) = groupSummary(widget, senderId, "Alice")
            .toConversationReference(self, emptyMap(), resources, paymentRequests = requests)
            .lastMessagePreview

        assertEquals("Alice sent request", groupPreview(other))
        assertEquals("You sent request", groupPreview(self))
    }

    @Test
    fun `a payment request the device doesn't hold yet has nothing to preview`() {
        assertNull(preview(listOf(MessageContent.Widget(WidgetContent.PaymentRequest(requestId)))))
    }

    @Test
    fun `a widget this client can't draw has nothing to preview`() {
        assertNull(preview(listOf(MessageContent.Widget(WidgetContent.Unsupported))))
    }

    @Test
    fun `a group notice previews as its own text with no prefix`() {
        assertEquals(
            "Alex joined the group",
            groupReference(listOf(MessageContent.System("Alex joined the group")), senderId = null).lastMessagePreview,
        )
    }

    @Test
    fun `a notice with blank text has no preview`() {
        assertNull(groupReference(listOf(MessageContent.System("  \u0085")), senderId = null).lastMessagePreview)
    }

    @Test
    fun `a join notice names who joined from the roster`() {
        every { resources.getString(Res.string.label_chat_system_joined, "Alice") } returns "Alice joined the group"
        val notice = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(other, admittedBy = null))
        assertEquals("Alice joined the group", groupReference(listOf(notice), senderId = null).lastMessagePreview)
    }

    @Test
    fun `a join notice naming someone unknown previews as its fallback and asks for them`() {
        val stranger: ID = listOf(5)
        val notice = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(stranger, admittedBy = null))
        assertEquals("Someone joined", groupReference(listOf(notice), senderId = null).lastMessagePreview)
        assertEquals(stranger, groupSummary(listOf(notice), senderId = null).unnamedGroupSender(self, emptyMap()))
    }

    @Test
    fun `a join notice whose users are all named asks for no one`() {
        val notice = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(self, admittedBy = other))
        assertNull(groupSummary(listOf(notice), senderId = null).unnamedGroupSender(self, emptyMap()))
    }

    @Test
    fun `an invite-link join previews with the inviter's name, for every viewer form`() {
        val inviter: ID = listOf(6)
        every { resources.getString(Res.string.label_chat_system_joinedViaInvite, "Alice", "Jeff") } returns "Alice joined with Jeff’s invite link"
        every { resources.getString(Res.string.label_chat_system_joinedSelfViaInvite, "Alice") } returns "You joined with Alice’s invite link"
        every { resources.getString(Res.string.label_chat_system_joinedViaYourInvite, "Alice") } returns "Alice joined with your invite link"
        // Named through the roster: `other` is Alice; the inviter is Jeff.
        val roster = mapOf(inviter.hexEncodedString() to profile("Jeff"))

        val others = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(other, null, inviter))
        assertEquals("Alice joined with Jeff’s invite link", groupReference(listOf(others), senderId = null, senderProfiles = roster).lastMessagePreview)

        val viewer = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(self, null, other))
        assertEquals("You joined with Alice’s invite link", groupReference(listOf(viewer), senderId = null).lastMessagePreview)

        val host = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(other, null, self))
        assertEquals("Alice joined with your invite link", groupReference(listOf(host), senderId = null).lastMessagePreview)
    }

    @Test
    fun `an invite-link join whose inviter is unknown asks for them`() {
        val stranger: ID = listOf(5)
        val notice = MessageContent.System("Someone joined", MessageContent.SystemEvent.UserJoined(self, null, stranger))
        assertEquals(stranger, groupSummary(listOf(notice), senderId = null).unnamedGroupSender(self, emptyMap()))
    }
}
