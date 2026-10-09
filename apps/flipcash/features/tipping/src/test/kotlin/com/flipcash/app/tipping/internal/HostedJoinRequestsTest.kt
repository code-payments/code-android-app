package com.flipcash.app.tipping.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ChatSummary
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HostedJoinRequestsTest {

    private val self = listOf<Byte>(9)
    private val other = listOf<Byte>(8)

    private fun group(id: Byte, title: String, creator: List<Byte>?, isPrivate: Boolean = true) = ChatSummary(
        metadata = ChatMetadata(
            chatId = ChatId(ByteArray(16) { id }.toList()),
            type = ChatType.GROUP,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(1_000),
            title = title,
            creator = creator,
            isPrivate = isPrivate,
        ),
        unreadCount = 0,
    )

    private fun member(id: Byte, enteredAt: Long) = LobbyMember(
        userProfile = UserProfile(
            displayName = "user $id",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            userId = listOf(id),
        ),
        publicKey = PublicKey("11111111111111111111111111111111"),
        enteredAt = Instant.fromEpochSeconds(enteredAt),
    )

    private val moony = group(1, "Moony", self)
    private val yotes = group(2, "Yotes", self)

    @Test
    fun `only private groups the viewer created count as hosted`() {
        val hosted = hostedPrivateGroups(
            listOf(moony, group(3, "Theirs", other), group(4, "Public", self, isPrivate = false), group(5, "Rebuilt", null)),
            self,
        )
        assertEquals(mapOf(moony.metadata.chatId to "Moony"), hosted)
    }

    @Test
    fun `no viewer, nothing hosted`() {
        assertTrue(hostedPrivateGroups(listOf(moony), null).isEmpty())
    }

    @Test
    fun `no row while nobody waits`() {
        val hosted = hostedPrivateGroups(listOf(moony, yotes), self)
        assertNull(hostedJoinRequests(hosted, emptyMap()))
        assertNull(hostedJoinRequests(hosted, mapOf(moony.metadata.chatId to emptyList())))
    }

    @Test
    fun `the row names the group with the newest request and counts its lobby`() {
        val hosted = hostedPrivateGroups(listOf(moony, yotes), self)
        val row = hostedJoinRequests(
            hosted,
            mapOf(
                moony.metadata.chatId to listOf(member(1, 100), member(2, 150), member(3, 120)),
                yotes.metadata.chatId to listOf(member(4, 300)),
            ),
        )
        assertEquals(HostedJoinRequests(yotes.metadata.chatId, "Yotes", 1), row)
    }

    @Test
    fun `a lobby for a group the viewer does not host is ignored`() {
        val theirs = group(3, "Theirs", other)
        val hosted = hostedPrivateGroups(listOf(moony, theirs), self)
        assertNull(hostedJoinRequests(hosted, mapOf(theirs.metadata.chatId to listOf(member(1, 100)))))
    }

    @Test
    fun `the row shows only under All`() {
        val row = HostedJoinRequests(moony.metadata.chatId, "Moony", 2)
        assertTrue(ChatsViewModel.State(joinRequests = row).showsJoinRequestsRow)
        assertFalse(ChatsViewModel.State(joinRequests = row, filter = ChatListFilter.Unread).showsJoinRequestsRow)
        assertFalse(ChatsViewModel.State().showsJoinRequestsRow)
    }
}
