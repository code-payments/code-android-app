package com.flipcash.app.messenger.internal.invites

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.chat.InviteId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
import com.flipcash.shared.chat.InviteSettings
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

/** Frames 2a to 2e: load, the undecryptable row, revoke and its DENIED, and create. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageInvitesViewModelTest {

    private val chatId = ChatId(ByteArray(32) { 5 })
    private val hostInvites = mockk<HostInvites>()
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(any()) } returns "text"
    }
    private val listed = MutableStateFlow<Map<ChatId, List<HostInvite>>>(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
        every { hostInvites.state } returns listed
        every { hostInvites.current(chatId) } returns null
        every { hostInvites.revokeAccess(chatId) } returns Unit
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = ManageInvitesViewModel(hostInvites, resources)

    private var seed = 0
    private fun hostInvite(
        url: String? = "https://x/chat/invite#s=a",
        status: Invite.Status = Invite.Status.ACTIVE,
    ): HostInvite {
        val n = seed++
        val invite = mockk<Invite>(relaxed = true) {
            every { id } returns InviteId(ByteArray(32) { n.toByte() })
            every { this@mockk.status } returns status
            every { createdAt } returns Clock.System.now()
            every { expiresAt } returns null
        }
        return HostInvite(invite, url)
    }

    @Test
    fun `load puts active links first and marks the current one`() = runTest {
        val active = hostInvite()
        val revoked = hostInvite(status = Invite.Status.REVOKED)
        listed.value = mapOf(chatId to listOf(active, revoked))
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(active, revoked))
        every { hostInvites.current(chatId) } returns active

        val model = viewModel()
        model.open(chatId)

        assertEquals(listOf(active), model.state.value.active)
        assertEquals(listOf(revoked), model.state.value.inactive)
        assertEquals(active, model.state.value.current)
        assertFalse(model.state.value.isEmpty)
    }

    @Test
    fun `no invites at all is the empty state`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())

        val model = viewModel()
        model.open(chatId)

        assertTrue(model.state.value.isEmpty)
    }

    @Test
    fun `a link this device can't open stays in the list without a link`() = runTest {
        val locked = hostInvite(url = null)
        listed.value = mapOf(chatId to listOf(locked))
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(locked))

        val model = viewModel()
        model.open(chatId)

        assertFalse(model.state.value.active.single().canShare)
    }

    @Test
    fun `a failed load is not an empty list`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.failure(IllegalStateException())

        val model = viewModel()
        model.open(chatId)

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isEmpty)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `a denied load takes the access away and leaves the screen`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.failure(GetChatInvitesError.Denied())

        val model = viewModel()
        model.open(chatId)

        assertTrue(model.effects.first() is ManageInvitesViewModel.Effect.LostAccess)
        verify { hostInvites.revokeAccess(chatId) }
    }

    @Test
    fun `a denied revoke takes the access away and leaves the screen`() = runTest {
        val active = hostInvite()
        listed.value = mapOf(chatId to listOf(active))
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(active))
        coEvery { hostInvites.revoke(chatId, active.invite.id) } returns Result.failure(RevokeInviteError.Denied())
        val model = viewModel()
        model.open(chatId)

        model.confirmRevoke(active)
        BottomBarManager.messages.value.single().actions.single().onClick()

        assertTrue(model.effects.first() is ManageInvitesViewModel.Effect.LostAccess)
        verify { hostInvites.revokeAccess(chatId) }
    }

    @Test
    fun `revoking asks first and a created link closes the new link sheet`() = runTest {
        val active = hostInvite()
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(active))
        val created = hostInvite()
        coEvery { hostInvites.create(chatId, InviteSettings.Default) } returns InviteCreation.Created(created)
        val model = viewModel()
        model.open(chatId)

        model.confirmRevoke(active)
        assertTrue(BottomBarManager.messages.value.single().showCancel)

        model.create(InviteSettings.Default)
        assertTrue(model.effects.first() is ManageInvitesViewModel.Effect.Created)
        assertFalse(model.state.value.creating)
    }
}
