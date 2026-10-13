package com.flipcash.app.tipping.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.ChatSummary
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RequestsProjectionTest {

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
    fun `nobody waiting, no hosted requests`() {
        val hosted = hostedPrivateGroups(listOf(moony, yotes), self).keys
        assertTrue(hostedRequests(hosted, emptyMap()).isEmpty())
        assertTrue(hostedRequests(hosted, mapOf(moony.metadata.chatId to emptyList())).isEmpty())
    }

    @Test
    fun `hosted groups with someone waiting, newest request first`() {
        val hosted = hostedPrivateGroups(listOf(moony, yotes), self).keys
        val requests = hostedRequests(
            hosted,
            mapOf(
                moony.metadata.chatId to listOf(member(1, 100), member(2, 150), member(3, 120)),
                yotes.metadata.chatId to listOf(member(4, 300)),
            ),
        )
        assertEquals(listOf(yotes.metadata.chatId, moony.metadata.chatId), requests.map { it.first })
        assertEquals(listOf(1, 3), requests.map { it.second.size })
    }

    @Test
    fun `a lobby for a group the viewer does not host is ignored`() {
        val theirs = group(3, "Theirs", other)
        val hosted = hostedPrivateGroups(listOf(moony, theirs), self).keys
        assertTrue(hostedRequests(hosted, mapOf(theirs.metadata.chatId to listOf(member(1, 100)))).isEmpty())
    }

    @Test
    fun `sent requests are newest first and skip a group not fetched yet`() {
        val requested = mapOf(
            moony.metadata.chatId to Instant.fromEpochSeconds(100),
            yotes.metadata.chatId to Instant.fromEpochSeconds(300),
            ChatId(ByteArray(16) { 7 }.toList()) to Instant.fromEpochSeconds(200),
        )
        val sent = sentRequests(
            requested,
            mapOf(moony.metadata.chatId to moony.metadata, yotes.metadata.chatId to yotes.metadata),
        )
        assertEquals(listOf(yotes.metadata, moony.metadata), sent.map { it.first })
        assertEquals(Instant.fromEpochSeconds(300), sent.first().second)
    }
}
