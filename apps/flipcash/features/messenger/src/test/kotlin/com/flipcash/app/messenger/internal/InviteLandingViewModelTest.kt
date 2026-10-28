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
import com.getcode.manager.SelectedBottomBarAction
import com.flipcash.features.messenger.R
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val requests = MutableStateFlow<Map<ChatId, Instant>>(emptyMap())
    private val requestedAt = Instant.fromEpochMilliseconds(5_000)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { lobby.observe() } returns requests
        every { resources.getString(R.string.title_inviteLinkNotFound) } returns "Link Not Found"
        every { resources.getString(R.string.description_inviteLinkNotFoundAlert) } returns "This invite link isn’t valid. Ask whoever sent it for a new one"
        every { resources.getString(R.string.error_title_groupNotFound) } returns "Group Not Found"
        every { resources.getString(R.string.description_inviteGroupGone) } returns "This group no longer exists"
        every { resources.getString(R.string.title_inviteBanned) } returns "You Can’t Join This Group"
        every { resources.getString(R.string.description_inviteBannedAlert) } returns "The host has removed you from this group"
    }

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

    private fun openLive(): InviteLandingViewModel {
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page()
        return viewModel().apply { open(secret) }
    }

    private fun dismissAlert() {
        val alert = BottomBarManager.messages.value.single()
        alert.onClose(SelectedBottomBarAction(index = -1))
    }

    private suspend fun assertPopsAfterAlert(
        result: InviteJoinResult,
        title: String,
        body: String,
    ) {
        BottomBarManager.clear()
        coEvery { landing.join(any(), chatId, token) } returns result
        val vm = openLive()
        vm.join()

        val alert = BottomBarManager.messages.value.single()
        assertEquals(title, alert.title)
        assertEquals(body, alert.subtitle)
        // Still on the preview, and re-enabled, until the alert is dismissed.
        assertEquals(InviteLandingState.Phase.Preview, vm.state.value.phase)
        assertEquals(false, vm.state.value.joining)

        dismissAlert()
        assertEquals(InviteLandingViewModel.Effect.Pop, vm.effects.first())
    }

    @Test
    fun `join with an unknown invite alerts then pops the preview`() = runTest(mainCoroutineRule.dispatcher) {
        assertPopsAfterAlert(
            InviteJoinResult.NotFound,
            "Link Not Found",
            "This invite link isn’t valid. Ask whoever sent it for a new one",
        )
    }

    @Test
    fun `join into a group that is gone alerts then pops the preview`() = runTest(mainCoroutineRule.dispatcher) {
        assertPopsAfterAlert(InviteJoinResult.GroupGone, "Group Not Found", "This group no longer exists")
    }

    @Test
    fun `join when banned alerts then pops the preview`() = runTest(mainCoroutineRule.dispatcher) {
        assertPopsAfterAlert(
            InviteJoinResult.Banned,
            "You Can’t Join This Group",
            "The host has removed you from this group",
        )
    }

    @Test
    fun `the open-time not-found and banned lookups keep their full-screen frames`() {
        coEvery { landing.lookup(any()) } returnsMany listOf(InviteLookup.NotFound, InviteLookup.Banned)
        val notFound = viewModel().apply { open(secret) }
        assertEquals(InviteLandingState.Phase.NotFound, notFound.state.value.phase)
        val banned = viewModel().apply { open(secret) }
        assertEquals(InviteLandingState.Phase.Banned, banned.state.value.phase)
        assertTrue(BottomBarManager.messages.value.isEmpty())
    }

    @Test
    fun `a viewer who already asked sees the request on a dead link`() {
        requests.value = mapOf(chatId to requestedAt)
        coEvery { landing.lookup(any()) } returns found(DeadInviteReason.EXPIRED)
        val vm = viewModel().apply { open(secret) }
        assertTrue(vm.state.value.dead)
        assertEquals(requestedAt, vm.state.value.requestedAt)
    }

    @Test
    fun `a request for another chat is not this one's`() {
        requests.value = mapOf(ChatId(ByteArray(16) { 6 }.toList()) to requestedAt)
        val vm = openLive()
        assertNull(vm.state.value.requestedAt)
    }

    @Test
    fun `a viewer waiting in the lobby still sees a live link as joinable`() {
        requests.value = mapOf(chatId to requestedAt)
        val vm = openLive()
        assertEquals(false, vm.state.value.dead)
        assertEquals(InviteLandingState.Phase.Preview, vm.state.value.phase)
    }

    @Test
    fun `a request that leaves the store leaves the screen`() {
        requests.value = mapOf(chatId to requestedAt)
        val vm = openLive()
        requests.value = emptyMap()
        assertNull(vm.state.value.requestedAt)
    }

    @Test
    fun `joining clears the stored request for that chat`() = runTest(mainCoroutineRule.dispatcher) {
        requests.value = mapOf(chatId to requestedAt)
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Joined(chatId)
        val vm = openLive()
        vm.join()
        coVerify(exactly = 1) { lobby.clear(chatId) }
        assertEquals(InviteLandingViewModel.Effect.OpenChat(chatId), vm.effects.first())
    }

    @Test
    fun `a join that does not land leaves the stored request alone`() {
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Dead(DeadInviteReason.REVOKED)
        val vm = openLive()
        vm.join()
        coVerify(exactly = 0) { lobby.clear(any()) }
    }

    @Test
    fun `after joining the held secret is gone, so a second join does nothing`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { landing.join(any(), chatId, token) } returns InviteJoinResult.Joined(chatId)
        val vm = openLive()
        vm.join()
        vm.join()
        coVerify(exactly = 1) { landing.join(any(), any(), any()) }
    }

    @Test
    fun `a double tap on join makes one call`() = runTest(mainCoroutineRule.dispatcher) {
        val gate = CompletableDeferred<InviteJoinResult>()
        coEvery { landing.join(any(), chatId, token) } coAnswers { gate.await() }
        val vm = openLive()
        vm.join()
        vm.join()
        assertTrue(vm.state.value.joining)
        gate.complete(InviteJoinResult.Joined(chatId))
        coVerify(exactly = 1) { landing.join(any(), any(), any()) }
    }

    @Test
    fun `requesting to join records the time and a failure alerts`() {
        every { resources.getString(R.string.error_title_failedToRequestJoin) } returns "Couldn't Request"
        every { resources.getString(R.string.error_description_failedToRequestJoin) } returns "Try again"
        coEvery { lobby.request(chatId) } returnsMany
            listOf(Result.failure(RuntimeException("offline")), Result.success(requestedAt))
        coEvery { landing.lookup(any()) } returns found(DeadInviteReason.EXPIRED)
        val vm = viewModel().apply { open(secret) }

        vm.requestToJoin()
        assertNull(vm.state.value.requestedAt)
        assertEquals(false, vm.state.value.requesting)
        assertEquals("Couldn't Request", BottomBarManager.messages.value.single().title)

        vm.requestToJoin()
        assertEquals(requestedAt, vm.state.value.requestedAt)
        assertEquals(false, vm.state.value.requesting)
    }

    @Test
    fun `cancelling clears the request, and a failed cancel keeps it`() {
        requests.value = mapOf(chatId to requestedAt)
        coEvery { landing.lookup(any()) } returns found(DeadInviteReason.EXPIRED)
        coEvery { lobby.cancel(chatId) } returnsMany
            listOf(Result.failure(RuntimeException("offline")), Result.success(Unit))
        val vm = viewModel().apply { open(secret) }
        assertEquals(requestedAt, vm.state.value.requestedAt)

        vm.cancelRequest()
        assertEquals(requestedAt, vm.state.value.requestedAt)
        assertEquals(false, vm.state.value.requesting)

        vm.cancelRequest()
        assertNull(vm.state.value.requestedAt)
        assertEquals(false, vm.state.value.requesting)
    }

    @Test
    fun `request and cancel before the group has loaded do nothing`() {
        val vm = viewModel()
        vm.requestToJoin()
        vm.cancelRequest()
        coVerify(exactly = 0) { lobby.request(any()) }
        coVerify(exactly = 0) { lobby.cancel(any()) }
    }

    @Test
    fun `roster pages append in order`() {
        val first = mockk<ChatMember>(relaxed = true)
        val second = mockk<ChatMember>(relaxed = true)
        val next = listOf<Byte>(2)
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns InviteRosterResult.Page(listOf(first), next, 48)
        coEvery { landing.roster(any(), any(), next) } returns InviteRosterResult.Page(listOf(second), null, 49)
        val vm = viewModel().apply { open(secret) }
        assertEquals(listOf(first, second), vm.state.value.members)
        assertEquals(49L, vm.state.value.memberCount)
    }

    @Test
    fun `roster paging stops at the row cap`() {
        val a = listOf<Byte>(1)
        val b = listOf<Byte>(2)
        val rows = List(100) { mockk<ChatMember>(relaxed = true) }
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns InviteRosterResult.Page(rows, a, 500)
        coEvery { landing.roster(any(), any(), a) } returns InviteRosterResult.Page(rows, b, 500)
        coEvery { landing.roster(any(), any(), b) } returns InviteRosterResult.Page(rows, null, 500)
        val vm = viewModel().apply { open(secret) }
        assertEquals(200, vm.state.value.members.size)
        coVerify(exactly = 0) { landing.roster(any(), any(), b) }
    }

    @Test
    fun `a failed later page keeps the rows already loaded`() {
        val next = listOf<Byte>(2)
        coEvery { landing.lookup(any()) } returns found()
        coEvery { landing.roster(any(), any(), null) } returns page(next)
        coEvery { landing.roster(any(), any(), next) } returns InviteRosterResult.Failed
        val vm = viewModel().apply { open(secret) }
        assertEquals(listOf(member), vm.state.value.members)
        assertEquals(false, vm.state.value.dead)
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
