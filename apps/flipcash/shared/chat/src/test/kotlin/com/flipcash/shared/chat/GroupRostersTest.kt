package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.RosterSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class GroupRostersTest {

    private val chatController = mockk<ChatController>()
    private val rosters = GroupRosters(chatController)

    private val chat = ChatId("aa")
    private val summary = RosterSummary(memberCount = 2, version = 5)

    private fun member(id: Byte, joinedAt: Long, version: Long = joinedAt, name: String = "user $id") =
        ChatMember(
            userId = listOf(id),
            userProfile = UserProfile(
                displayName = name,
                socialAccounts = emptyList(),
                phoneNumber = null,
                email = null,
                userId = listOf(id),
            ),
            pointers = emptyList(),
            joinedAt = Instant.fromEpochSeconds(joinedAt),
            version = version,
        )

    private val host = member(1, 100)
    private val joiner = member(2, 200)

    private fun ids() = rosters.state.value[chat]?.map { it.userId }

    private suspend fun loaded(vararg members: ChatMember) {
        coEvery { chatController.getRoster(chat, any()) } returns
            Result.success(RosterPage(members.toList(), summary, null, hasMore = false))
        rosters.load(chat).getOrThrow()
    }

    @Test
    fun `load reads every page and orders most recently joined first`() = runTest {
        val token: PagingToken = listOf(7)
        coEvery { chatController.getRoster(chat, match { it.token == null }) } returns
            Result.success(RosterPage(listOf(host), summary, token, hasMore = true))
        coEvery { chatController.getRoster(chat, match { it.token == token }) } returns
            Result.success(RosterPage(listOf(joiner), summary, null, hasMore = false))

        rosters.load(chat).getOrThrow()

        assertEquals(listOf(listOf<Byte>(2), listOf<Byte>(1)), ids())
    }

    @Test
    fun `a member on two pages keeps the greater version`() = runTest {
        val renamed = member(2, 200, version = 300, name = "renamed")
        coEvery { chatController.getRoster(chat, any()) } returns
            Result.success(RosterPage(listOf(renamed, joiner), summary, null, hasMore = false))

        rosters.load(chat).getOrThrow()

        assertEquals("renamed", rosters.state.value[chat]?.single()?.userProfile?.displayName)
    }

    @Test
    fun `changes before a load are ignored`() {
        rosters.apply(chat, listOf(RosterChange.MemberJoined(joiner, null, summary)))

        assertNull(ids())
    }

    @Test
    fun `joins and leaves apply to a loaded roster`() = runTest {
        loaded(host)

        rosters.apply(chat, listOf(RosterChange.MemberJoined(joiner, null, summary)))
        assertEquals(listOf(listOf<Byte>(2), listOf<Byte>(1)), ids())

        rosters.apply(chat, listOf(RosterChange.MemberLeft(listOf(2), summary)))
        assertEquals(listOf(listOf<Byte>(1)), ids())
    }

    @Test
    fun `an older join does not replace a newer one`() = runTest {
        loaded(member(2, 200, version = 9, name = "current"))

        rosters.apply(chat, listOf(RosterChange.MemberJoined(member(2, 200, version = 3, name = "stale"), null, summary)))

        assertEquals("current", rosters.state.value[chat]?.single()?.userProfile?.displayName)
    }

    @Test
    fun `clear drops every roster`() = runTest {
        loaded(host)

        rosters.clear()

        assertNull(ids())
    }
}
