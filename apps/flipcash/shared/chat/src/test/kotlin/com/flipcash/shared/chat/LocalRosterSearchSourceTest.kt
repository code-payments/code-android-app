package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatRosterDataSource
import com.flipcash.app.persistence.sources.RosterSearchCandidate
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.LocalRosterSearchSource
import com.flipcash.shared.chat.internal.RosterSync
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class LocalRosterSearchSourceTest {

    private val chatId = ChatId("c0ffee")
    private val self = listOf<Byte>(1)

    private val rosterDataSource = mockk<ChatRosterDataSource>()
    private val rosterSync = mockk<RosterSync>(relaxed = true)
    private val userManager = mockk<UserManager> { every { accountId } returns self }

    private val subject = LocalRosterSearchSource(rosterDataSource, rosterSync, userManager)

    private fun candidate(id: Int, name: String, username: String? = null, spokeAt: Long? = null) =
        RosterSearchCandidate(listOf(id.toByte()), name, username, null, spokeAt)

    @Test
    fun `an empty query returns the recent speakers, most recent first`() = runTest {
        coEvery { rosterDataSource.recentSpeakers(chatId, self, any()) } returns
            listOf(candidate(2, "Old", spokeAt = 1), candidate(3, "New", spokeAt = 2))

        assertEquals(listOf("New", "Old"), subject.search(chatId, "@").map { it.displayName })
        coVerify(exactly = 0) { rosterDataSource.searchByPrefix(any(), any(), any(), any()) }
    }

    @Test
    fun `the index is asked for the query folded, by its longest word`() = runTest {
        coEvery { rosterDataSource.searchByPrefix(chatId, self, "garc", any()) } returns
            listOf(candidate(2, "María García"), candidate(3, "Luis García"))

        val names = subject.search(chatId, "@Ma Garc").map { it.displayName }

        // Every word must start one of the member's words.
        assertEquals(listOf("María García"), names)
    }

    @Test
    fun `results are capped at the limit`() = runTest {
        coEvery { rosterDataSource.searchByPrefix(chatId, self, "a", any()) } returns
            (10..30).map { candidate(it, "A$it") }

        assertEquals(5, subject.search(chatId, "a", limit = 5).size)
    }

    @Test
    fun `nobody is signed in, nothing is searched`() = runTest {
        every { userManager.accountId } returns null

        assertEquals(emptyList(), subject.search(chatId, "a"))
    }

    @Test
    fun `refresh rereads the first roster page`() = runTest {
        subject.refresh(chatId)

        coVerify { rosterSync.refreshFirstPage(chatId) }
    }
}
