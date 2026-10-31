package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun id(byte: Int): ID = listOf(byte.toByte())

private fun profile(name: String = "", username: String? = null): UserProfile = UserProfile(
    displayName = name,
    socialAccounts = emptyList(),
    phoneNumber = null,
    email = null,
    username = username,
)

private fun member(userId: ID, name: String) = ChatMember(
    userId = userId,
    userProfile = profile(name),
    pointers = emptyList(),
)

class ReactorNameResolverTest {

    private val self = id(1)
    private val alice = id(2)

    @Test
    fun `self always resolves to the self label, ignoring every other source`() {
        val resolved = ReactorNameResolver.resolve(
            userId = self,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = profile("Self Vanity Name"),
            members = listOf(member(self, "Self Member Name")),
            senderProfiles = mapOf(self.hexEncodedString() to profile("Self Sender Name")),
        )
        assertEquals("You", resolved)
    }

    @Test
    fun `cached profile wins over the member roster and sender profiles`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = profile("Cached Alice"),
            members = listOf(member(alice, "Member Alice")),
            senderProfiles = mapOf(alice.hexEncodedString() to profile("Sender Alice")),
        )
        assertEquals("Cached Alice", resolved)
    }

    @Test
    fun `member roster wins over sender profiles when there is no cached profile`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = null,
            members = listOf(member(alice, "Member Alice")),
            senderProfiles = mapOf(alice.hexEncodedString() to profile("Sender Alice")),
        )
        assertEquals("Member Alice", resolved)
    }

    @Test
    fun `sender profiles resolve a reactor absent from the cache and the roster`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = null,
            members = emptyList(),
            senderProfiles = mapOf(alice.hexEncodedString() to profile("Sender Alice")),
        )
        assertEquals("Sender Alice", resolved)
    }

    @Test
    fun `null when none of the four sources has an answer yet`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = null,
            members = emptyList(),
            senderProfiles = emptyMap(),
        )
        assertNull(resolved)
    }

    @Test
    fun `a source with a blank name and no handle is treated as not resolving`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = profile(name = "", username = null),
            members = listOf(member(alice, "Member Alice")),
            senderProfiles = emptyMap(),
        )
        assertEquals("Member Alice", resolved)
    }

    @Test
    fun `a blank display name falls back to the handle within the same source`() {
        val resolved = ReactorNameResolver.resolve(
            userId = alice,
            selfUserId = self,
            selfLabel = "You",
            cachedProfile = profile(name = "", username = "alice"),
            members = emptyList(),
            senderProfiles = emptyMap(),
        )
        assertEquals("@alice", resolved)
    }
}
