package com.flipcash.app.messenger.internal.invites

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.chat.InviteId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
import com.flipcash.shared.chat.InviteExpiry
import com.flipcash.shared.chat.InviteSettings
import com.flipcash.shared.chat.InviteUses
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
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
    fun `revoking asks first, and a created link clears the creating state`() = runTest {
        val active = hostInvite()
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(active))
        val created = hostInvite()
        coEvery { hostInvites.create(chatId, InviteSettings.Default) } returns InviteCreation.Created(created)
        val model = viewModel()
        model.open(chatId)

        model.confirmRevoke(active)
        assertTrue(BottomBarManager.messages.value.single().showCancel)

        model.create(InviteSettings.Default)
        assertFalse(model.state.value.creating)
    }

    @Test
    fun `create sends the settings the sheet returned and shows creating until it finishes`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        val gate = kotlinx.coroutines.CompletableDeferred<InviteCreation>()
        val settings = InviteSettings(InviteExpiry.OneDay, InviteUses.Five)
        coEvery { hostInvites.create(chatId, settings) } coAnswers { gate.await() }
        val model = viewModel()
        model.open(chatId)

        model.create(settings)
        assertTrue(model.state.value.creating)

        gate.complete(InviteCreation.Created(hostInvite()))
        assertFalse(model.state.value.creating)
        coVerify(exactly = 1) { hostInvites.create(chatId, settings) }
    }

    @Test
    fun `a created link becomes the current one`() = runTest {
        val created = hostInvite()
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        coEvery { hostInvites.create(chatId, InviteSettings.Default) } coAnswers {
            listed.value = mapOf(chatId to listOf(created))
            every { hostInvites.current(chatId) } returns created
            InviteCreation.Created(created)
        }
        val model = viewModel()
        model.open(chatId)

        model.create(InviteSettings.Default)

        assertEquals(created, model.state.value.current)
        assertEquals(listOf(created), model.state.value.active)
    }

    @Test
    fun `too many from inside Manage Invites is OK only`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        coEvery { hostInvites.create(chatId, any()) } returns InviteCreation.TooMany
        val model = viewModel()
        model.open(chatId)

        model.create(InviteSettings.Default)

        val message = BottomBarManager.messages.value.single()
        assertFalse(message.showCancel)
        assertEquals(1, message.actions.size)
        assertFalse(model.state.value.creating)
    }

    @Test
    fun `denied and failed creates each show one message and re-enable the button`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        val model = viewModel()
        model.open(chatId)

        coEvery { hostInvites.create(chatId, any()) } returns InviteCreation.NotAvailable
        model.create(InviteSettings.Default)
        assertEquals(1, BottomBarManager.messages.value.size)
        assertFalse(model.state.value.creating)

        BottomBarManager.clear()
        coEvery { hostInvites.create(chatId, any()) } returns InviteCreation.Failed(IllegalStateException())
        model.create(InviteSettings.Default)
        assertEquals(1, BottomBarManager.messages.value.size)
        assertFalse(model.state.value.creating)
    }

    @Test
    fun `a second create while one is in flight is ignored`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        coEvery { hostInvites.create(chatId, any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val model = viewModel()
        model.open(chatId)

        model.create(InviteSettings.Default)
        model.create(InviteSettings.Default)

        assertTrue(model.state.value.creating)
        coVerify(exactly = 1) { hostInvites.create(chatId, any()) }
    }

    @Test
    fun `a failed revoke shows an error and leaves the list as it was`() = runTest {
        val active = hostInvite()
        listed.value = mapOf(chatId to listOf(active))
        coEvery { hostInvites.load(chatId) } returns Result.success(listOf(active))
        coEvery { hostInvites.revoke(chatId, active.invite.id) } returns Result.failure(IllegalStateException())
        val model = viewModel()
        model.open(chatId)

        model.confirmRevoke(active)
        val confirm = BottomBarManager.messages.value.single()
        confirm.actions.single().onClick()

        assertEquals(listOf(active), model.state.value.active)
        // The confirmation is still up (the UI removes it on a tap); the error is the second.
        assertEquals(2, BottomBarManager.messages.value.size)
        assertTrue(BottomBarManager.messages.value.last().isError)
        verify(exactly = 0) { hostInvites.revokeAccess(any()) }
    }

    @Test
    fun `refresh after a failed first load loads again`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.failure(IllegalStateException())
        val model = viewModel()
        model.open(chatId)
        assertTrue(model.state.value.failed)

        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        model.refresh()

        assertFalse(model.state.value.failed)
        assertTrue(model.state.value.isEmpty)
    }
}
