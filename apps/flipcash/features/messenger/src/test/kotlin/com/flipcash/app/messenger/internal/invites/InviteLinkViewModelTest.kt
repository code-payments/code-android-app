package com.flipcash.app.messenger.internal.invites

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.RecordingAnalytics
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.core.extensions.SensitiveClipKey
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.flipcash.shared.chat.GroupCardLinks
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SelectedBottomBarAction
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * The invite variant of the share sheet (frames 1b, 1f, 1c, 1e). The sheet opens from the tap on
 * Share Invite Link, so opening with no current link makes one; each way CreateInvite can end maps
 * to its own alert; a specific invite from Manage Invites never creates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InviteLinkViewModelTest {

    private val chatId = ChatId(ByteArray(32) { 5 })
    private val hostInvites = mockk<HostInvites>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(any<Int>()) } returns "text"
    }
    private val clipboard =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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

    private val analytics = RecordingAnalytics()

    private val approved = mutableSetOf<ChatId>()
    private val approveStore = object : ApproveNewMembersStore {
        override fun observe(chatId: ChatId): Flow<Boolean> = flowOf(chatId in approved)
        override suspend fun set(chatId: ChatId, on: Boolean) {
            if (on) approved += chatId else approved -= chatId
        }
    }

    private fun viewModel() =
        InviteLinkViewModel(hostInvites, resources, clipboard, analytics, GroupCardLinks(), approveStore)

    private var seed = 1
    private fun hostInvite(url: String?, useCount: Long = 0, maxUses: Long = 0): HostInvite {
        val n = seed++
        val invite = mockk<Invite>(relaxed = true) {
            every { id } returns InviteId(ByteArray(32) { n.toByte() })
            every { expiresAt } returns Instant.fromEpochSeconds(1_800_000_000)
            every { this@mockk.useCount } returns useCount
            every { this@mockk.maxUses } returns maxUses
        }
        return HostInvite(invite, url)
    }

    @Test
    fun `opening with no current link creates at once and goes through creating to ready`() = runTest {
        val gate = CompletableDeferred<InviteCreation>()
        coEvery { hostInvites.currentOrCreate(chatId) } coAnswers { gate.await() }
        val model = viewModel()

        model.open(chatId, null)
        assertEquals(InviteLinkState.Creating, model.state.value.link)
        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }

        gate.complete(InviteCreation.Created(hostInvite("https://x/chat/invite#s=new", maxUses = 5)))
        val ready = model.state.value.link as InviteLinkState.Ready
        assertEquals("https://x/chat/invite#s=new", ready.url)
        assertEquals(5L, ready.maxUses)
    }

    @Test
    fun `tiles do nothing while the link is being made`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val model = viewModel()
        model.open(chatId, null)

        model.onShare()
        model.onCopy()
        model.onSend()

        assertEquals(InviteLinkState.Creating, model.state.value.link)
        assertEquals(null, clipboard.primaryClip)
        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }
    }

    @Test
    fun `an existing current link opens ready without creating`() = runTest {
        every { hostInvites.current(chatId) } returns hostInvite("https://x/chat/invite#s=a", useCount = 2, maxUses = 10)
        val model = viewModel()
        model.open(chatId, null)

        val ready = model.state.value.link as InviteLinkState.Ready
        assertEquals(2L, ready.useCount)
        model.onShare()

        assertEquals("https://x/chat/invite#s=a", (model.effects.first() as InviteLinkViewModel.Effect.Share).url)
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
    }

    @Test
    fun `copy puts the link on the clipboard marked sensitive and says copied`() = runTest {
        every { hostInvites.current(chatId) } returns hostInvite("https://x/chat/invite#s=a")
        val model = viewModel()
        model.open(chatId, null)

        model.onCopy()

        val clip = clipboard.primaryClip!!
        assertEquals("https://x/chat/invite#s=a", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean(SensitiveClipKey))
        assertEquals(InviteLinkViewModel.Effect.Copied, model.effects.first())
    }

    @Test
    fun `send hands the link to the picked chats`() = runTest {
        every { hostInvites.current(chatId) } returns hostInvite("https://x/chat/invite#s=a")
        val model = viewModel()
        model.open(chatId, null)

        model.onSend()

        assertEquals("https://x/chat/invite#s=a", (model.effects.first() as InviteLinkViewModel.Effect.Send).url)
    }

    @Test
    fun `denied shows 1e with the join link on offer and closes behind it`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.NotAvailable
        val model = viewModel()
        model.open(chatId, null)

        assertEquals(InviteLinkState.None, model.state.value.link)
        val message = BottomBarManager.messages.value.single()
        assertTrue(message.showCancel)
        message.actions.first().onClick()
        assertEquals(InviteLinkViewModel.Effect.OpenJoinLink, model.effects.first())
        coVerify(exactly = 1) { hostInvites.currentOrCreate(chatId) }
    }

    @Test
    fun `dismissing 1e closes the sheet`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.NotAvailable
        val model = viewModel()
        model.open(chatId, null)

        BottomBarManager.messages.value.single().onClose(SelectedBottomBarAction(-1))

        assertEquals(InviteLinkViewModel.Effect.Close, model.effects.first())
    }

    @Test
    fun `too many offers Manage Invites and Cancel`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.TooMany
        val model = viewModel()
        model.open(chatId, null)

        val message = BottomBarManager.messages.value.single()
        assertTrue(message.showCancel)
        message.actions.first().onClick()
        assertEquals(InviteLinkViewModel.Effect.OpenManageInvites, model.effects.first())
        assertEquals(InviteLinkState.None, model.state.value.link)
    }

    @Test
    fun `not available turns Approve new members on and opens the plain link`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.NotAvailable
        val model = viewModel()
        model.open(chatId, null)

        BottomBarManager.messages.value.single().actions.first().onClick()

        assertEquals(InviteLinkViewModel.Effect.OpenJoinLink, model.effects.first())
        assertTrue(chatId in approved)
    }

    @Test
    fun `any other failure shows the generic error and closes behind it`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.Failed(IllegalStateException())
        val model = viewModel()
        model.open(chatId, null)

        assertEquals(InviteLinkState.None, model.state.value.link)
        BottomBarManager.messages.value.single().onClose(SelectedBottomBarAction(-1))
        assertEquals(InviteLinkViewModel.Effect.Close, model.effects.first())
    }

    @Test
    fun `a denied list at open takes the access away and closes`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.failure(GetChatInvitesError.Denied())
        val model = viewModel()

        model.open(chatId, null)

        verify { hostInvites.revokeAccess(chatId) }
        assertEquals(InviteLinkViewModel.Effect.Close, model.effects.first())
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
    }

    @Test
    fun `a list that fails to load makes nothing`() = runTest {
        coEvery { hostInvites.load(chatId) } returns Result.failure(IllegalStateException())
        val model = viewModel()

        model.open(chatId, null)

        assertEquals(InviteLinkState.None, model.state.value.link)
        coVerify(exactly = 0) { hostInvites.currentOrCreate(any()) }
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
    fun `reopening on a different invite reloads and on the same one does nothing`() = runTest {
        val first = hostInvite("https://x/chat/invite#s=one")
        val second = hostInvite("https://x/chat/invite#s=two")
        listed.value = mapOf(chatId to listOf(first, second))
        val model = viewModel()

        model.open(chatId, first.invite.id.toHex())
        model.open(chatId, first.invite.id.toHex())
        coVerify(exactly = 1) { hostInvites.load(chatId) }
        assertEquals("https://x/chat/invite#s=one", (model.state.value.link as InviteLinkState.Ready).url)

        model.open(chatId, second.invite.id.toHex())
        coVerify(exactly = 2) { hostInvites.load(chatId) }
        assertEquals("https://x/chat/invite#s=two", (model.state.value.link as InviteLinkState.Ready).url)
    }

    @Test
    fun `state and effects never print the link`() {
        val ready = InviteLinkState.Ready("https://x/chat/invite#s=secret", null, 0, 0)
        assertFalse(ready.toString().contains("secret"))
        assertFalse(InviteLinkViewModel.Effect.Share("https://x/chat/invite#s=secret").toString().contains("secret"))
        assertFalse(InviteLinkViewModel.Effect.Send("https://x/chat/invite#s=secret").toString().contains("secret"))
    }

    @Test
    fun `making the link because none exists sends Invite Link Created`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns
            InviteCreation.Created(hostInvite("https://x/chat/invite#s=new"))
        val model = viewModel()
        model.open(chatId, null)

        assertEquals(
            listOf(GroupEvents.inviteLinkCreated(state = AnalyticsState.SUCCESS, error = null)),
            analytics.events,
        )
    }

    @Test
    fun `a refused create sends Invite Link Created with its error`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.TooMany
        viewModel().open(chatId, null)

        assertEquals(
            listOf(GroupEvents.inviteLinkCreated(state = AnalyticsState.FAILURE, error = "TooManyInvites")),
            analytics.events,
        )
    }

    @Test
    fun `opening on a link that exists makes none and sends nothing`() = runTest {
        every { hostInvites.current(chatId) } returns hostInvite("https://x/chat/invite#s=a")
        viewModel().open(chatId, null)

        assertTrue(analytics.events.isEmpty())
    }
}
