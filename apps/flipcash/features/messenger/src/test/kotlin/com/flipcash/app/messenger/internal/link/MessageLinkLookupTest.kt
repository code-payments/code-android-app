package com.flipcash.app.messenger.internal.link

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class MessageLinkLookupTest {

    private val groupId = ChatId(UUID.fromString("6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162").bytes)
    private val dmId = ChatId(List(32) { it.toByte() })
    private val myId = UUID.fromString("2b0b4d1e-9f3e-4c21-9f1a-6d5f7c8e9a0b").bytes
    private val senderId = UUID.fromString("8cec60c8-afbb-88a8-96fe-64cc825f5322").bytes
    private val sentAt = Instant.fromEpochSeconds(1_760_000_000)

    private fun card(chatId: ChatId = groupId, isGroup: Boolean = true) = LinkCard.Message(
        url = "https://app.flipcash.com/chat/x/m/42",
        start = 0,
        end = 10,
        chatId = chatId,
        messageId = 42,
        isGroup = isGroup,
    )

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        joinedAt = null,
        userId = null,
        username = null,
    )

    private fun chat(
        chatId: ChatId = groupId,
        type: ChatType = ChatType.GROUP,
        isPrivate: Boolean = false,
        members: List<ChatMember> = listOf(ChatMember(senderId, profile("Ada"), emptyList())),
    ) = ChatMetadata(
        chatId = chatId,
        type = type,
        members = members,
        lastMessage = null,
        lastActivity = sentAt,
        title = "Moony",
        isPrivate = isPrivate,
        creator = senderId,
    )

    private fun message(
        content: MessageContent = MessageContent.Text("gm"),
        redacted: Boolean = false,
        reactions: ReactionSummary? = null,
    ) = ChatMessage(
        messageId = 42,
        senderId = senderId,
        content = listOf(content),
        timestamp = sentAt,
        unreadSeq = 0,
        reactions = reactions,
        redacted = redacted,
    )

    private class Deps(
        val chats: ChatCoordinator = mock(),
        val chatController: ChatController = mock(),
        val messaging: ChatMessagingController = mock(),
        val profiles: ProfileController = mock(),
        val knownProfiles: UserProfileDataSource = mock(),
        val groups: GroupLinkLookup = mock(),
    )

    private fun lookup(deps: Deps) = MessageLinkLookup(
        chats = deps.chats,
        chatController = deps.chatController,
        messaging = deps.messaging,
        profiles = deps.profiles,
        knownProfiles = deps.knownProfiles,
        userManager = mock<UserManager> { on { accountId } doReturn myId },
        groups = deps.groups,
    )

    private fun stored(metadata: ChatMetadata?, isMember: Boolean?): ChatCoordinator = mock {
        on { observeMetadata(any()) } doReturn flowOf(metadata?.let { ChatMembership(it, isMember) })
    }

    @Test
    fun `a member sees the message with its reaction count`() = runTest {
        val reaction = mock<EmojiReaction> { on { count } doReturn 3 }
        val other = mock<EmojiReaction> { on { count } doReturn 2 }
        val chats = stored(chat(), isMember = true)
        org.mockito.kotlin.whenever(chats.readMessage(groupId, 42))
            .thenReturn(Result.success(message(reactions = ReactionSummary(42, listOf(reaction, other)))))

        val state = lookup(Deps(chats = chats))(card()).getOrThrow()

        val resolved = assertIs<LinkCard.Message.State.Resolved>(state)
        assertEquals(5L, resolved.reactionCount)
        assertEquals("Ada", resolved.message.author)
        assertEquals(LinkCard.Message.Body.Text("gm"), resolved.message.body)
        assertEquals(LinkCard.Message.Kind.PublicGroup, resolved.source.kind)
    }

    @Test
    fun `a member of a private group reads the message through the chat, not GetMessage`() = runTest {
        val deps = Deps(chats = stored(chat(isPrivate = true), isMember = true))
        org.mockito.kotlin.whenever(deps.chats.readMessage(groupId, 42)).thenReturn(Result.success(message()))

        val state = lookup(deps)(card()).getOrThrow()

        assertEquals(
            LinkCard.Message.Kind.PrivateGroup,
            assertIs<LinkCard.Message.State.Resolved>(state).source.kind,
        )
        verify(deps.messaging, never()).getMessage(any(), any(), any())
    }

    @Test
    fun `a non-member of a public group gets the message as GetMessage hands a stranger`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat()))
        org.mockito.kotlin.whenever(deps.messaging.getMessage(groupId, 42, ViewMode.FULL_OR_REDACTED))
            .thenReturn(Result.success(message(content = MessageContent.Text("•••"), redacted = true)))
        org.mockito.kotlin.whenever(deps.profiles.getProfileForUser(any()))
            .thenReturn(Result.success(profile("Ada")))

        val state = lookup(deps)(card()).getOrThrow()

        val gated = assertIs<LinkCard.Message.State.Gated>(state)
        assertEquals(LinkCard.Message.Body.Text("•••", redacted = true), gated.message.body)
        assertEquals("Ada", gated.message.author)
    }

    @Test
    fun `a sender outside the roster is named from a profile the app already has`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat(members = emptyList())))
        org.mockito.kotlin.whenever(deps.messaging.getMessage(groupId, 42, ViewMode.FULL_OR_REDACTED))
            .thenReturn(Result.success(message()))
        org.mockito.kotlin.whenever(deps.knownProfiles.getCachedProfile(senderId)).thenReturn(profile("Ted"))

        val state = lookup(deps)(card()).getOrThrow()

        assertEquals("Ted", assertIs<LinkCard.Message.State.Gated>(state).message.author)
        org.mockito.kotlin.verify(deps.profiles, org.mockito.kotlin.never()).getProfileForUser(any())
    }

    @Test
    fun `a redacted photo carries no blob, only its BlurHash`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat()))
        org.mockito.kotlin.whenever(deps.messaging.getMessage(groupId, 42, ViewMode.FULL_OR_REDACTED))
            .thenReturn(Result.success(message(content = MessageContent.Media(emptyList(), caption = null), redacted = true)))
        org.mockito.kotlin.whenever(deps.profiles.getProfileForUser(any()))
            .thenReturn(Result.success(profile("Ada")))

        val state = lookup(deps)(card()).getOrThrow()

        val body = assertIs<LinkCard.Message.Body.Photo>(assertIs<LinkCard.Message.State.Gated>(state).message.body)
        assertEquals(null, body.photo)
        assertEquals(null, body.caption)
    }

    @Test
    fun `a non-member of a private group sees the group's record and never the message`() = runTest {
        val deps = Deps(chats = stored(chat(isPrivate = true), isMember = false))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat(isPrivate = true)))
        org.mockito.kotlin.whenever(deps.profiles.getProfileForUser(any()))
            .thenReturn(Result.success(profile("Ada")))

        val state = lookup(deps)(card()).getOrThrow()

        val locked = assertIs<LinkCard.Message.State.LockedGroup>(state)
        assertEquals("Ada", locked.host)
        verify(deps.messaging, never()).getMessage(any(), any(), any())
    }

    @Test
    fun `unknown membership is treated as not a member`() = runTest {
        val deps = Deps(chats = stored(chat(isPrivate = true), isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat(isPrivate = true)))
        org.mockito.kotlin.whenever(deps.profiles.getProfileForUser(any()))
            .thenReturn(Result.failure(IOException()))

        val state = lookup(deps)(card()).getOrThrow()

        assertIs<LinkCard.Message.State.LockedGroup>(state)
        verify(deps.chats, never()).readMessage(any(), any())
    }

    @Test
    fun `a DM the viewer isn't in is locked without asking the server`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))

        val state = lookup(deps)(card(chatId = dmId, isGroup = false)).getOrThrow()

        assertEquals(LinkCard.Message.State.LockedDirect, state)
        verify(deps.chatController, never()).getChat(any(), any())
        verify(deps.messaging, never()).getMessage(any(), any(), any())
    }

    @Test
    fun `a member's DM names the peer`() = runTest {
        val dm = chat(
            chatId = dmId,
            type = ChatType.CONTACT_DM,
            members = listOf(
                ChatMember(myId, profile("Me"), emptyList()),
                ChatMember(senderId, profile("Ada"), emptyList()),
            ),
        )
        val deps = Deps(chats = stored(dm, isMember = true))
        org.mockito.kotlin.whenever(deps.chats.readMessage(dmId, 42)).thenReturn(Result.success(message()))

        val state = lookup(deps)(card(chatId = dmId, isGroup = false)).getOrThrow()

        val source = assertIs<LinkCard.Message.State.Resolved>(state).source
        assertEquals("Ada", source.title)
        assertEquals(LinkCard.Message.Kind.DirectMessage, source.kind)
    }

    @Test
    fun `a deleted message is unresolved`() = runTest {
        val deps = Deps(chats = stored(chat(), isMember = true))
        org.mockito.kotlin.whenever(deps.chats.readMessage(groupId, 42))
            .thenReturn(Result.success(message(content = MessageContent.Deleted(sentAt, senderId))))

        assertEquals(LinkCard.Message.State.Unresolved, lookup(deps)(card()).getOrThrow())
    }

    @Test
    fun `a message the server doesn't have is unresolved`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.success(chat()))
        org.mockito.kotlin.whenever(deps.messaging.getMessage(groupId, 42, ViewMode.FULL_OR_REDACTED))
            .thenReturn(Result.failure(GetMessageError.NotFound()))

        assertEquals(LinkCard.Message.State.Unresolved, lookup(deps)(card()).getOrThrow())
    }

    @Test
    fun `a group the server doesn't have is unresolved`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.failure(GetChatError.NotFound()))

        assertEquals(LinkCard.Message.State.Unresolved, lookup(deps)(card()).getOrThrow())
    }

    @Test
    fun `a request that didn't complete fails, so the resolver asks again`() = runTest {
        val deps = Deps(chats = stored(null, isMember = null))
        org.mockito.kotlin.whenever(deps.chatController.getChat(groupId, ViewMode.REDACTED))
            .thenReturn(Result.failure(IOException("offline")))

        assertTrue(lookup(deps)(card()).isFailure)
    }

    @Test
    fun `a payment shows its amount`() = runTest {
        val amount = Fiat(5.0)
        val cash = MessageContent.Cash(
            intentId = senderId,
            amount = amount,
            mint = mock(),
            action = MessageContent.Cash.Action.TIPPED,
        )
        val deps = Deps(chats = stored(chat(), isMember = true))
        org.mockito.kotlin.whenever(deps.chats.readMessage(groupId, 42))
            .thenReturn(Result.success(message(content = cash)))

        val body = assertIs<LinkCard.Message.State.Resolved>(lookup(deps)(card()).getOrThrow()).message.body

        assertEquals(LinkCard.Message.Body.Cash(amount = amount.formatted(), tipped = true), body)
    }
}
