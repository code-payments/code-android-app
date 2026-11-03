package com.flipcash.app.messenger.internal

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BanState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.RestrictedUsersPage
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.shared.chat.GroupRosters
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * The member sheet and Members swipe read who is restricted once per flow, then follow the host's
 * own Restrict and Lift writes, wherever they were made, without reading again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberRestrictionsTest {

    private val chatController = mockk<ChatController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val toasts = mockk<SystemToastController>(relaxed = true)
    private val writes = GroupRestrictionWrites()
    private val chatId = ChatId(ByteArray(32) { 3 }.toList())
    private val otherChat = ChatId(ByteArray(32) { 4 }.toList())
    private val soon = SilenceState.Until(Clock.System.now() + 1.hours)

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

    private fun viewModel() = MemberModerationViewModel(
        chatController, resources, mockk<GroupRosters>(relaxed = true), RecordingAnalytics(), toasts, writes,
    )

    @Test
    fun `the read keeps silences and drops removed members`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns Result.success(
            RestrictedUsersPage(
                listOf(user(1, silence = soon), user(2, silence = SilenceState.Forever, ban = BanState(Clock.System.now())), user(3)),
                null,
                false,
            )
        )
        val vm = viewModel().also { it.loadRestrictions(chatId) }
        assertEquals(mapOf(listOf<Byte>(1) to soon), vm.restrictions.value)
    }

    @Test
    fun `the read happens once per chat`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(emptyList(), null, false))
        val vm = viewModel()
        vm.loadRestrictions(chatId)
        vm.loadRestrictions(chatId)
        coVerify(exactly = 1) { chatController.getRestrictedUsers(chatId, any()) }
    }

    @Test
    fun `a write landing while the read is in flight wins over the read`() {
        val answer = CompletableDeferred<Result<RestrictedUsersPage>>()
        coEvery { chatController.getRestrictedUsers(chatId, any()) } coAnswers { answer.await() }
        val vm = viewModel().also { it.loadRestrictions(chatId) }
        writes.lifted(chatId, listOf(1))
        answer.complete(Result.success(RestrictedUsersPage(listOf(user(1, silence = soon)), null, false)))
        assertEquals(emptyMap(), vm.restrictions.value)
    }

    @Test
    fun `Restrict from the duration sheet marks the member`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(emptyList(), null, false))
        val vm = viewModel().also { it.loadRestrictions(chatId) }
        writes.silenced(chatId, listOf(5), SilenceState.Forever)
        writes.silenced(otherChat, listOf(6), SilenceState.Forever)
        assertEquals(mapOf(listOf<Byte>(5) to SilenceState.Forever), vm.restrictions.value)
    }

    @Test
    fun `Lift clears the member once the server agrees`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(listOf(user(1, silence = soon)), null, false))
        coEvery { chatController.unsilenceUser(chatId, listOf(1)) } returns Result.success(Unit)
        val vm = viewModel().also { it.loadRestrictions(chatId) }
        vm.lift(chatId, listOf(1), "User 1")
        assertEquals(emptyMap(), vm.restrictions.value)
    }

    @Test
    fun `a failed Lift leaves the member restricted`() {
        coEvery { chatController.getRestrictedUsers(chatId, any()) } returns
            Result.success(RestrictedUsersPage(listOf(user(1, silence = soon)), null, false))
        coEvery { chatController.unsilenceUser(chatId, listOf(1)) } returns Result.failure(IllegalStateException())
        val vm = viewModel().also { it.loadRestrictions(chatId) }
        vm.lift(chatId, listOf(1), "User 1")
        assertEquals(mapOf(listOf<Byte>(1) to soon), vm.restrictions.value)
    }
}
