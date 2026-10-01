package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetMentionSuggestionsError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MentionSuggestion
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.MentionSuggestionPool
import com.flipcash.shared.chat.internal.ServerRosterSearchSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ServerRosterSearchSourceTest {

    private val chatId = ChatId("c0ffee")
    private val self = listOf<Byte>(1)

    private val chatController = mockk<ChatController>()
    private val profiles = mockk<UserProfileDataSource>(relaxed = true)
    private val userManager = mockk<UserManager> { every { accountId } returns self }
    private val pool = MentionSuggestionPool(chatController, profiles)

    private fun TestScope.subject() = ServerRosterSearchSource(pool, userManager, backgroundScope)

    private fun suggestion(id: Int, name: String, username: String, sentAt: Long? = null) = MentionSuggestion(
        userProfile = UserProfile.Empty.copy(userId = listOf(id.toByte()), displayName = name, username = username),
        lastSentAt = sentAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    private val erin = suggestion(2, "Erin", "erin", sentAt = 300)
    private val bob = suggestion(3, "Bob", "bob", sentAt = 200)
    private val eli = suggestion(4, "Eli Zane", "eli", sentAt = 100)

    private fun serverReturns(vararg pool: MentionSuggestion) {
        coEvery { chatController.getMentionSuggestions(chatId) } returns Result.success(pool.toList())
    }

    private suspend fun RosterSearchSource.names(query: String, limit: Int = 20) =
        search(chatId, query, limit).map { it.displayName }

    @Test
    fun `filtering keeps the server's order`() = runTest {
        serverReturns(erin, bob, eli)
        val subject = subject()

        assertEquals(listOf("Erin", "Eli Zane"), subject.names("@e"))
        // Any word of the display name, and the handle.
        assertEquals(listOf("Eli Zane"), subject.names("@zan"))
        assertEquals(listOf("Bob"), subject.names("@bo"))
    }

    @Test
    fun `a bare at shows the head of the pool`() = runTest {
        serverReturns(erin, bob, eli)

        assertEquals(listOf("Erin", "Bob"), subject().names("@", limit = 2))
    }

    @Test
    fun `the pool is fetched once per composing session`() = runTest {
        serverReturns(erin, bob, eli)
        val subject = subject()

        subject.refresh(chatId)
        subject.names("@")
        subject.names("@e")
        subject.names("@er")
        coVerify(exactly = 1) { chatController.getMentionSuggestions(chatId) }

        subject.refresh(chatId)
        subject.names("@")
        coVerify(exactly = 2) { chatController.getMentionSuggestions(chatId) }
    }

    @Test
    fun `the pool's profiles are stored`() = runTest {
        serverReturns(erin, bob)

        subject().names("@")

        coVerify { profiles.store(erin.userProfile.userId!!, erin.userProfile) }
        coVerify { profiles.store(bob.userProfile.userId!!, bob.userProfile) }
    }

    @Test
    fun `a new message moves a held sender to the front`() = runTest {
        serverReturns(erin, bob, eli)
        val subject = subject()
        subject.names("@")

        pool.onMessages(chatId, listOf(eli.userProfile.userId!! to Instant.fromEpochMilliseconds(400)))

        assertEquals(listOf("Eli Zane", "Erin", "Bob"), subject.names("@"))
    }

    @Test
    fun `a message older than the sender's last one, or from someone not in the pool, changes nothing`() = runTest {
        serverReturns(erin, bob, eli)
        val subject = subject()
        subject.names("@")

        pool.onMessages(
            chatId,
            listOf(
                bob.userProfile.userId!! to Instant.fromEpochMilliseconds(150),
                listOf<Byte>(9) to Instant.fromEpochMilliseconds(500),
            ),
        )

        assertEquals(listOf("Erin", "Bob", "Eli Zane"), subject.names("@"))
    }

    @Test
    fun `the caller is never suggested`() = runTest {
        serverReturns(erin, suggestion(1, "Me", "me"))

        assertEquals(listOf("Erin"), subject().names("@"))
    }

    @Test
    fun `DENIED returns nothing`() = runTest {
        coEvery { chatController.getMentionSuggestions(chatId) } returns
            Result.failure(GetMentionSuggestionsError.Denied())
        val subject = subject()

        assertEquals(emptyList(), subject.names("@e"))
        subject.refresh(chatId)
        assertEquals(emptyList(), subject.names("@"))
    }

    @Test
    fun `NOT_FOUND returns nothing`() = runTest {
        coEvery { chatController.getMentionSuggestions(chatId) } returns
            Result.failure(GetMentionSuggestionsError.NotFound())

        assertEquals(emptyList(), subject().names("@"))
    }

    @Test
    fun `a transport error returns nothing`() = runTest {
        coEvery { chatController.getMentionSuggestions(chatId) } returns
            Result.failure(GetMentionSuggestionsError.Other(java.io.IOException("UNAVAILABLE")))

        assertEquals(emptyList(), subject().names("@"))
    }
}
