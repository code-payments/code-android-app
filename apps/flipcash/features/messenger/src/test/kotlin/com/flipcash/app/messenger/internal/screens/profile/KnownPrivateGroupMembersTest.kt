package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMessage
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class KnownPrivateGroupMembersTest {

    private val host: ID = listOf(1)
    private val me: ID = listOf(2)
    private val poster: ID = listOf(3)
    private val stranger: ID = listOf(4)

    private fun profiles(vararg ids: ID): Map<String, UserProfile> =
        ids.associate { it.hexEncodedString() to mockk<UserProfile>(relaxed = true) }

    private fun message(id: Long, sender: ID?) = ChatMessage(
        messageId = id,
        senderId = sender,
        content = emptyList(),
        timestamp = Instant.fromEpochSeconds(id),
        unreadSeq = 0,
    )

    @Test
    fun `posters are distinct, newest first, skipping messages with no sender`() {
        val alice: ID = listOf(10)
        val bob: ID = listOf(11)
        val messages = listOf(message(1, alice), message(2, bob), message(3, null), message(4, alice))
        assertEquals(listOf(alice, bob), postersNewestFirst(messages))
    }

    @Test
    fun `host first, then the viewer, then posters, each once`() {
        val members = knownPrivateGroupMembers(
            creator = host,
            selfId = me,
            posters = listOf(poster, host, me),
            profiles = profiles(host, me, poster),
        )
        assertEquals(listOf(host, me, poster), members.map { it.userId })
    }

    @Test
    fun `anyone without a profile is left out`() {
        val members = knownPrivateGroupMembers(
            creator = null,
            selfId = me,
            posters = listOf(stranger),
            profiles = profiles(me),
        )
        assertEquals(listOf(me), members.map { it.userId })
    }
}
