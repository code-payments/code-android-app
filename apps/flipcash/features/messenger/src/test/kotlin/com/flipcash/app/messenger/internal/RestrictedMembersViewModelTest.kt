package com.flipcash.app.messenger.internal

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BanState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.RestrictedUsersPage
import com.flipcash.services.models.chat.SilenceState
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * The host's two lists come from one read split on the client, and Unsilence and Unban edit that
 * read locally because the server's index trails writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestrictedMembersViewModelTest {

    private val chatController = mockk<ChatController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val chatId = ChatId(ByteArray(32) { 3 }.toList())

    private val soon = SilenceState.Until(Clock.System.now() + 1.hours)
    private val lapsed = SilenceState.Until(Clock.System.now() - 1.hours)
    private val banned = BanState(Clock.System.now())

    private val silencedOnly = user(1, silence = soon)
    private val silencedForever = user(2, silence = SilenceState.Forever)
    private val lapsedOnly = user(3, silence = lapsed)
    private val removed = user(4, ban = banned)
    private val removedAndSilenced = user(5, silence = SilenceState.Forever, ban = banned)

    private fun user(id: Int, silence: SilenceState? = null, ban: BanState? = null) = RestrictedUser(
        userProfile = UserProfile(
            displayName = "User $id", socialAccounts = emptyList(), phoneNumber = null, email = null,
            userId = listOf(id.toByte()),
        ),
        silence = silence,
        ban = ban,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun loaded(vararg users: RestrictedUser): RestrictedMembersViewModel {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(users.toList(), null, false))
        return RestrictedMembersViewModel(chatController, resources).also { it.load(chatId) }
    }

    @Test
    fun `silenced holds active silences with no ban, removed holds every ban`() {
        val state = loaded(silencedOnly, silencedForever, lapsedOnly, removed, removedAndSilenced).state.value

        assertEquals(listOf(silencedOnly, silencedForever), state.rows(RestrictedKind.Silenced))
        assertEquals(listOf(removed, removedAndSilenced), state.rows(RestrictedKind.Removed))
        assertFalse(state.loading)
    }

    @Test
    fun `every page is read before the lists show`() {
        coEvery { chatController.getRestrictedUsers(chatId, QueryOptions()) } returns
            Result.success(RestrictedUsersPage(listOf(silencedOnly), listOf(9), true))
        coEvery { chatController.getRestrictedUsers(chatId, QueryOptions(token = listOf(9))) } returns
            Result.success(RestrictedUsersPage(listOf(removed), null, false))

        val vm = RestrictedMembersViewModel(chatController, resources).also { it.load(chatId) }

        assertEquals(listOf(silencedOnly, removed), vm.state.value.users)
    }

    @Test
    fun `loading the same chat again does not read twice`() {
        val vm = loaded(silencedOnly)
        vm.load(chatId)

        coVerify(exactly = 1) { chatController.getRestrictedUsers(chatId, any()) }
    }

    @Test
    fun `a failed read is flagged and can be retried`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns Result.failure(Throwable("down"))
        val vm = RestrictedMembersViewModel(chatController, resources).also { it.load(chatId) }

        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.loading)

        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(listOf(removed), null, false))
        vm.load(chatId)
        assertEquals(listOf(removed), vm.state.value.users)
    }

    @Test
    fun `unsilence drops the row without reading the list again`() {
        coEvery { chatController.unsilenceUser(chatId, any()) } returns Result.success(Unit)
        val vm = loaded(silencedOnly, silencedForever)

        vm.unsilence(chatId, silencedOnly.userProfile.userId!!)

        assertEquals(listOf(silencedForever), vm.state.value.rows(RestrictedKind.Silenced))
        assertTrue(vm.state.value.pending.isEmpty())
        coVerify(exactly = 1) { chatController.getRestrictedUsers(chatId, any()) }
    }

    @Test
    fun `unsilencing a removed member leaves them in removed, no longer silenced`() {
        coEvery { chatController.unsilenceUser(chatId, any()) } returns Result.success(Unit)
        val vm = loaded(removedAndSilenced)

        vm.unsilence(chatId, removedAndSilenced.userProfile.userId!!)

        assertEquals(listOf(removedAndSilenced.copy(silence = null)), vm.state.value.rows(RestrictedKind.Removed))
    }

    @Test
    fun `unban takes a removed member out and moves a silenced one back to silenced`() {
        coEvery { chatController.unbanUser(chatId, any()) } returns Result.success(Unit)
        val vm = loaded(removed, removedAndSilenced)

        vm.unban(chatId, removed.userProfile.userId!!)
        vm.unban(chatId, removedAndSilenced.userProfile.userId!!)

        assertTrue(vm.state.value.rows(RestrictedKind.Removed).isEmpty())
        assertEquals(listOf(removedAndSilenced.copy(ban = null)), vm.state.value.rows(RestrictedKind.Silenced))
    }

    @Test
    fun `a failed unsilence keeps the row and clears its spinner`() {
        coEvery { chatController.unsilenceUser(chatId, any()) } returns Result.failure(Throwable("denied"))
        val vm = loaded(silencedOnly)

        vm.unsilence(chatId, silencedOnly.userProfile.userId!!)

        assertEquals(listOf(silencedOnly), vm.state.value.rows(RestrictedKind.Silenced))
        assertTrue(vm.state.value.pending.isEmpty())
    }
}
