package com.flipcash.app.messenger.internal

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.invite.DeadInviteReason
import com.flipcash.shared.chat.invite.GroupInviteLanding
import com.flipcash.shared.chat.invite.InviteJoinResult
import com.flipcash.shared.chat.invite.InviteLookup
import com.flipcash.shared.chat.invite.InviteRosterResult
import com.getcode.manager.BottomBarManager
import com.flipcash.features.messenger.R
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Each way an invite link can land (nodes 12846:59 to 12846:160) and each way a join can end. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InviteLandingViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val landing = mockk<GroupInviteLanding>()
    private val lobby = mockk<LobbyRequests>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private val chatId = ChatId(ByteArray(16) { 5 }.toList())
    private val token = InviteToken(ByteArray(32) { 9 })
    private val secret = ByteArray(32) { 1 }
    private val member = mockk<ChatMember>(relaxed = true)

    private fun chat(count: Long = 48) = ChatMetadata(
        chatId = chatId,
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(1_000),
        title = "Moony",
        rosterSummary = RosterSummary(memberCount = count, version = 1),
    )

    private fun viewModel() = InviteLandingViewModel(landing, lobby, resources)

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun found(dead: DeadInviteReason? = null) = InviteLookup.Found(token, chat(), dead)

    private fun page(next: PagingToken? = null) = InviteRosterResult.Page(listOf(member), next, 48)

    @Test
    fun `a missing secret is the not-found frame`() {
        val vm = viewModel().apply { open(null) }
        assertEquals(InviteLandingState.Phase.NotFound, vm.state.value.phase)
    }

    @Test
    fun `an unknown invite is the not-found frame`() {
        coEvery { landing.lookup(any()) } returns InviteLookup.NotFound
        val vm = viewModel().apply { open(secret) }
        assertEquals(InviteLandingState.Phase.NotFound, vm.state.value.phase)
    }

    @Test
    fun `a banned viewer sees the banned frame`() {
        coEvery { landing.lookup(any()) } returns InviteLookup.Banned
        val vm = viewModel().apply { open(secret) }
        assertEquals(InviteLandingState.Phase.Banned, vm.state.value.phase)
    }

    @Test
    fun `a failed lookup is its own state`() {
        coEvery { landing.lookup(any()) } returns InviteLookup.Failed
        val vm = viewModel().apply { open(secret) }
        assertEquals(InviteLandingState.Phase.LoadFailed, vm.state.value.phase)
    }

    @Test
    fun `an existing member goes straight to the chat`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { landing.lookup(any()) } returns InviteLookup.AlreadyMember(chatId)
        val vm = viewModel().apply { open(secret) }
        assertEquals(InviteLandingViewModel.Effect.OpenChat(chatId), vm.effects.first())
    }

    @Test
    fun `a live link previews the group with its roster`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        val vm = viewModel().apply { open(secret) }
        val state = vm.state.value
        assertEquals(InviteLandingState.Phase.Preview, state.phase)
        assertEquals(false, state.dead)
        assertEquals(listOf(member), state.members)
        assertEquals("Moony", state.group?.title)
    }

    @Test
    fun `a dead link shows its reason and never fetches the roster`() {
        coEvery { landing.lookup(any()) } returns found(DeadInviteReason.EXPIRED)
        val vm = viewModel().apply { open(secret) }
        assertTrue(vm.state.value.dead)
        assertEquals(DeadInviteReason.EXPIRED, vm.state.value.deadReason)
        coVerify(exactly = 0) { landing.roster(any(), any(), any()) }
    }

    @Test
    fun `a roster refusal mid-paging keeps the loaded rows and goes dead`() {
        val next = listOf<Byte>(2)
        coEvery { landing.lookup(any()) } returnsMany listOf(found(), found(DeadInviteReason.REVOKED))
        coEvery { landing.roster(any(), any(), null) } returns page(next)
        coEvery { landing.roster(any(), any(), next) } returns InviteRosterResult.Denied
        val vm = viewModel().apply { open(secret) }
        val state = vm.state.value
        assertEquals(listOf(member), state.members)
        assertTrue(state.dead)
        assertEquals(DeadInviteReason.REVOKED, state.deadReason)
    }

    @Test
    fun `a refusal whose reason cannot be learned shows no guessed reason`() {
        val next = listOf<Byte>(2)
        coEvery { landing.lookup(any()) } returnsMany listOf(found(), InviteLookup.Failed)
        coEvery { landing.roster(any(), any(), null) } returns page(next)
        coEvery { landing.roster(any(), any(), next) } returns InviteRosterResult.Denied
        val vm = viewModel().apply { open(secret) }
        assertTrue(vm.state.value.dead)
        assertNull(vm.state.value.deadReason)
    }

    @Test
    fun `joining lands in the chat`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Joined(chatId)
        val vm = viewModel().apply { open(secret) }
        vm.join()
        assertEquals(InviteLandingViewModel.Effect.OpenChat(chatId), vm.effects.first())
    }

    @Test
    fun `a dead invite on join switches in place with no alert`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Dead(DeadInviteReason.EXHAUSTED)
        val vm = viewModel().apply { open(secret) }
        vm.join()
        val state = vm.state.value
        assertTrue(state.dead)
        assertEquals(DeadInviteReason.EXHAUSTED, state.deadReason)
        assertEquals(false, state.joining)
        assertTrue(BottomBarManager.messages.value.isEmpty())
    }

    @Test
    fun `join not-found and banned switch frames`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returnsMany
            listOf(InviteJoinResult.NotFound, InviteJoinResult.Banned)
        val vm = viewModel().apply { open(secret) }
        vm.join()
        assertEquals(InviteLandingState.Phase.NotFound, vm.state.value.phase)
    }

    @Test
    fun `join group-gone is its own frame`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.GroupGone
        val vm = viewModel().apply { open(secret) }
        vm.join()
        assertEquals(InviteLandingState.Phase.GroupGone, vm.state.value.phase)
    }

    @Test
    fun `a refused join stays on the preview with the generic alert`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Refused
        every { resources.getString(R.string.error_title_failedToJoinGroup) } returns "Couldn't Join Group"
        every { resources.getString(R.string.error_description_failedToJoinAlert) } returns "Something went wrong. Please try again"
        val vm = viewModel().apply { open(secret) }
        vm.join()
        assertEquals(InviteLandingState.Phase.Preview, vm.state.value.phase)
        assertEquals(false, vm.state.value.joining)
        val alert = BottomBarManager.messages.value.single()
        assertEquals("Couldn't Join Group", alert.title)
        assertEquals("Something went wrong. Please try again", alert.subtitle)
    }

    @Test
    fun `a failed join stays on the preview with the alert`() {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Failed
        val vm = viewModel().apply { open(secret) }
        vm.join()
        assertEquals(InviteLandingState.Phase.Preview, vm.state.value.phase)
        assertEquals(false, vm.state.value.joining)
        assertTrue(BottomBarManager.messages.value.isNotEmpty())
    }
}
