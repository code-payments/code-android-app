package com.flipcash.app.messenger.internal.invites

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.time.Instant

/**
 * The invite variant of the share sheet (frames 1b, 1f, 1c, 1e): nothing is made on open, the
 * first Share or Copy tap makes the link, and each way CreateInvite can end maps to its own alert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteLinkViewModelTest {

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
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        every { hostInvites.current(chatId) } returns null
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = InviteLinkViewModel(hostInvites, resources, mockk(relaxed = true))

    private fun hostInvite(url: String?, useCount: Long = 0, maxUses: Long = 0): HostInvite {
        val invite = mockk<Invite>(relaxed = true) {
            every { id } returns InviteId(ByteArray(32) { 1 })
            every { expiresAt } returns Instant.fromEpochSeconds(1_800_000_000)
            every { this@mockk.useCount } returns useCount
            every { this@mockk.maxUses } returns maxUses
        }
        return HostInvite(invite, url)
    }

    @Test
    fun `opening makes nothing`() = runTest {
        val model = viewModel()
        model.open(chatId, null)

        assertEquals(InviteLinkState.None, model.state.value.link)
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
    }

    @Test
    fun `an existing current link is shown and shared without creating`() = runTest {
        every { hostInvites.current(chatId) } returns hostInvite("https://x/chat/invite#s=a", useCount = 2, maxUses = 10)
        val model = viewModel()
        model.open(chatId, null)

        val ready = model.state.value.link as InviteLinkState.Ready
        assertEquals(2L, ready.useCount)
        model.onShare()

        val effect = model.effects.first() as InviteLinkViewModel.Effect.Share
        assertEquals("https://x/chat/invite#s=a", effect.url)
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
    }

    @Test
    fun `the first tap creates the link and then shares it`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns
            InviteCreation.Created(hostInvite("https://x/chat/invite#s=new"))
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()

        assertTrue(model.state.value.link is InviteLinkState.Ready)
        assertEquals("https://x/chat/invite#s=new", (model.effects.first() as InviteLinkViewModel.Effect.Share).url)
        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }
    }

    @Test
    fun `a second tap while creating does not call create again`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()
        model.onCopy()

        assertEquals(InviteLinkState.Creating, model.state.value.link)
        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }
    }

    @Test
    fun `denied shows the unavailable alert and a later tap does not call create again`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.NotAvailable
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()
        assertTrue(model.state.value.unavailable)
        assertEquals(1, BottomBarManager.messages.value.size)
        assertEquals(1, BottomBarManager.messages.value.single().actions.size)

        BottomBarManager.clear()
        model.onCopy()

        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `too many invites shows an alert and leaves the sheet ready to try again`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.TooMany
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()

        assertEquals(InviteLinkState.None, model.state.value.link)
        assertFalse(model.state.value.unavailable)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `any other failure shows the generic error and can be tried again`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.Failed(IllegalStateException())
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()

        assertEquals(InviteLinkState.None, model.state.value.link)
        assertFalse(model.state.value.unavailable)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `an invite picked in Manage Invites that can't be opened closes the sheet`() = runTest {
        val picked = hostInvite(url = null)
        listed.value = mapOf(chatId to listOf(picked))
        val model = viewModel()

        model.open(chatId, picked.invite.id.toHex())

        assertTrue(model.effects.first() is InviteLinkViewModel.Effect.Close)
    }

    @Test
    fun `a specific invite shares its own link and never creates`() = runTest {
        val picked = hostInvite("https://x/chat/invite#s=picked")
        listed.value = mapOf(chatId to listOf(picked))
        val model = viewModel()
        model.open(chatId, picked.invite.id.toHex())

        model.onShare()

        assertEquals("https://x/chat/invite#s=picked", (model.effects.first() as InviteLinkViewModel.Effect.Share).url)
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
    }

    @Test
    fun `state and effects never print the link`() {
        val ready = InviteLinkState.Ready("https://x/chat/invite#s=secret", null, 0, 0)
        assertFalse(ready.toString().contains("secret"))
        assertFalse(InviteLinkViewModel.Effect.Share("https://x/chat/invite#s=secret").toString().contains("secret"))
        assertFalse(InviteLinkViewModel.Effect.Send("https://x/chat/invite#s=secret").toString().contains("secret"))
    }
}
