package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DomainModelsTest {

    @Test
    fun `ChatType has expected values`() {
        assertEquals(4, ChatType.entries.size)
        assertIs<ChatType>(ChatType.UNKNOWN)
        assertIs<ChatType>(ChatType.CONTACT_DM)
        assertIs<ChatType>(ChatType.TIP_DM)
        assertIs<ChatType>(ChatType.GROUP)
    }

    @Test
    fun `PointerType has expected values`() {
        assertEquals(4, PointerType.entries.size)
        assertIs<PointerType>(PointerType.UNKNOWN)
        assertIs<PointerType>(PointerType.SENT)
        assertIs<PointerType>(PointerType.DELIVERED)
        assertIs<PointerType>(PointerType.READ)
    }

    @Test
    fun `TypingState has expected values`() {
        assertEquals(5, TypingState.entries.size)
    }

    @Test
    fun `MessageContent Text holds text`() {
        val content = MessageContent.Text("hello")
        assertEquals("hello", content.text)
    }

    @Test
    fun `ChatMessage can have null senderId`() {
        val msg = ChatMessage(
            messageId = 1,
            senderId = null,
            content = listOf(MessageContent.Text("system")),
            timestamp = Instant.fromEpochSeconds(1000),
            unreadSeq = 0,
        )
        assertNull(msg.senderId)
    }

    @Test
    fun `ChatUpdate aggregates all update types`() {
        val chatId = ChatId(ByteArray(32))
        val update = ChatUpdate(
            chatId = chatId,
            events = listOf(
                ChatEvent(
                    sequence = 1,
                    count = 1,
                    ts = Instant.fromEpochSeconds(0),
                    mutations = listOf(
                        ChatMutation.MessageSent(
                            ChatMessage(1, null, listOf(MessageContent.Text("hi")), Instant.fromEpochSeconds(0), 1)
                        )
                    ),
                )
            ),
            pointerUpdates = listOf(
                MessagePointer(PointerType.READ, listOf(1.toByte()), 5, Instant.fromEpochSeconds(0))
            ),
            typingNotifications = listOf(
                TypingNotification(listOf(1.toByte()), TypingState.STARTED_TYPING)
            ),
            metadataUpdates = listOf(
                MetadataUpdate.LastActivityChanged(Instant.fromEpochSeconds(100))
            ),
        )

        assertEquals(1, update.events.size)
        assertEquals(1, update.pointerUpdates.size)
        assertEquals(1, update.typingNotifications.size)
        assertEquals(1, update.metadataUpdates.size)
    }

    @Test
    fun `MetadataUpdate FullRefresh holds metadata`() {
        val metadata = ChatMetadata(
            chatId = ChatId(ByteArray(32)),
            type = ChatType.CONTACT_DM,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(500),
        )
        val update = MetadataUpdate.FullRefresh(metadata)
        assertEquals(metadata, update.metadata)
    }

    @Test
    fun `ChatMember holds profile and pointers`() {
        val member = ChatMember(
            userId = listOf(1.toByte()),
            userProfile = UserProfile("Test", emptyList(), null, null),
            pointers = listOf(MessagePointer(PointerType.READ, listOf(1.toByte()), 10, Instant.fromEpochSeconds(0))),
        )
        assertEquals("Test", member.userProfile.displayName)
        assertEquals(1, member.pointers.size)
    }

    @Test
    fun `ChatMember joinedAt and version default to null and zero`() {
        val member = ChatMember(
            userId = listOf(1.toByte()),
            userProfile = UserProfile("Test", emptyList(), null, null),
            pointers = emptyList(),
        )
        assertNull(member.joinedAt)
        assertEquals(0L, member.version)
    }

    @Test
    fun `ChatMember joinedAt and version round-trip`() {
        val joinedAt = Instant.fromEpochSeconds(500)
        val member = ChatMember(
            userId = listOf(1.toByte()),
            userProfile = UserProfile("Test", emptyList(), null, null),
            pointers = emptyList(),
            joinedAt = joinedAt,
            version = 3,
        )
        assertEquals(joinedAt, member.joinedAt)
        assertEquals(3L, member.version)
    }

    @Test
    fun `ViewerState permissions default to canEdit false`() {
        val state = ViewerState()
        assertEquals(false, state.permissions.canEdit)
    }

    @Test
    fun `ViewerState permissions round-trip`() {
        val state = ViewerState(permissions = ViewerState.Permissions(canEdit = true))
        assertEquals(true, state.permissions.canEdit)
    }

    @Test
    fun `MetadataUpdate TitleChanged holds the new title`() {
        val update = MetadataUpdate.TitleChanged("New title")
        assertEquals("New title", update.newTitle)
    }

    @Test
    fun `MetadataUpdate PictureChanged holds the new picture`() {
        val picture = MediaItem(renditions = emptyList())
        val update = MetadataUpdate.PictureChanged(picture)
        assertEquals(picture, update.newPicture)
    }

    @Test
    fun `RosterPage holds members, summary and paging state`() {
        val page = RosterPage(
            members = listOf(
                ChatMember(
                    userId = listOf(1.toByte()),
                    userProfile = UserProfile("Test", emptyList(), null, null),
                    pointers = emptyList(),
                )
            ),
            rosterSummary = RosterSummary(memberCount = 1, version = 1),
            pagingToken = listOf(0xAB.toByte()),
            hasMore = true,
        )
        assertEquals(1, page.members.size)
        assertEquals(1L, page.rosterSummary.version)
        assertEquals(true, page.hasMore)
    }

    @Test
    fun `EditChatParameters is a no-op when every field is unset`() {
        val parameters = EditChatParameters()
        assertNull(parameters.title)
        assertNull(parameters.picture)
    }
}
