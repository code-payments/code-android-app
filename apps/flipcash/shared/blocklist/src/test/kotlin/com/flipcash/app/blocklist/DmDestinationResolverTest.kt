package com.flipcash.app.blocklist

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.ChatCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class DmDestinationResolverTest {

    private val userId = List<Byte>(16) { 2 }
    private val dmChatId = ChatId(ByteArray(32) { 7 })

    private val members = MutableStateFlow<List<ChatMember>>(emptyList())
    private val blocked = MutableStateFlow(false)

    private val chatCoordinator = mockk<ChatCoordinator> {
        coEvery { generateChatId(userId) } returns Result.success(dmChatId)
        every { observeMembers(dmChatId) } returns members
    }
    private val blocklist = mockk<BlocklistCoordinator> {
        every { observeIsBlocked(userId) } returns blocked
    }
    private val resolver = DmDestinationResolver(chatCoordinator, blocklist)

    private val profile = AppRoute.Messaging.Profile(ProfileAddress.ById(userId), ProfileOrigin.UsernameLookup)

    @Test
    fun `no members opens the profile`() = runTest {
        assertEquals(profile, resolver.dmDestination(userId, ProfileOrigin.UsernameLookup))
    }

    @Test
    fun `members open the dm by chat id`() = runTest {
        members.value = listOf(mockk())

        assertEquals(
            AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(dmChatId)),
            resolver.dmDestination(userId, ProfileOrigin.UsernameLookup),
        )
    }

    @Test
    fun `blocked beats an existing dm`() = runTest {
        members.value = listOf(mockk())
        blocked.value = true

        assertEquals(profile, resolver.dmDestination(userId, ProfileOrigin.UsernameLookup))
    }

    @Test
    fun `a chat id that cannot be derived opens the profile`() = runTest {
        coEvery { chatCoordinator.generateChatId(userId) } returns Result.failure(IllegalStateException())
        members.value = listOf(mockk())

        assertEquals(profile, resolver.dmDestination(userId, ProfileOrigin.UsernameLookup))
    }

    @Test
    fun `the profile carries the origin of the caller`() = runTest {
        assertEquals(
            AppRoute.Messaging.Profile(ProfileAddress.ById(userId), ProfileOrigin.Transaction),
            resolver.dmDestination(userId, ProfileOrigin.Transaction),
        )
    }

    @Test
    fun `observing follows the dm appearing and a block landing`() = runTest {
        val seen = mutableListOf<AppRoute>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver.observeDmDestination(userId, ProfileOrigin.Transaction).collect { seen += it }
        }
        members.value = listOf(mockk())
        blocked.value = true
        job.cancel()

        val chat = AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(dmChatId))
        val txProfile = AppRoute.Messaging.Profile(ProfileAddress.ById(userId), ProfileOrigin.Transaction)
        assertEquals(listOf<AppRoute>(txProfile, chat, txProfile), seen)
    }
}
