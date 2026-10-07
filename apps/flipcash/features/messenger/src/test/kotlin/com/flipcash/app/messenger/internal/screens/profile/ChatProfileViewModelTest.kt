package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.model.financial.Fiat
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.getcode.opencode.model.financial.CurrencyCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Where a person's profile gets its name and join date from. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatProfileViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val theirId: List<Byte> = List(16) { 2 }
    private val cachedJoin = Instant.fromEpochSeconds(1_700_000_000)
    private val serverJoin = Instant.fromEpochSeconds(1_710_000_000)

    private val profiles = mockk<ProfileController>()
    private val featuredGroupsStore = FeaturedGroupsStore(mockk(relaxed = true))

    private val chatController = mockk<ChatController> {
        coEvery { getFeaturedGroups(any()) } returns Result.success(emptyList())
    }
    private val userManager = mockk<UserManager> { every { accountId } returns List(16) { 1 } }

    private val dmChatId = ChatId(ByteArray(32) { 7 })
    private val members = MutableStateFlow<List<ChatMember>>(emptyList())
    private val blocked = MutableStateFlow(false)
    private val fee = MutableStateFlow<Fiat?>(null)

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { generateChatId(any()) } returns Result.success(dmChatId)
        every { observeMembers(any()) } returns members
        every { observeMetadata(any()) } returns flowOf(null)
    }
    private val blocklist = mockk<BlocklistCoordinator>(relaxed = true) {
        every { observeIsBlocked(any()) } returns blocked
    }
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true) {
        every { startChattingFee(any()) } returns fee
    }
    private val payer = mockk<StartChattingPayer>(relaxed = true) {
        every { observeSelectedToken() } returns emptyFlow()
        coEvery { mayProceed(any(), any()) } returns true
    }

    private fun viewModel() = ChatProfileViewModel(
        contactCoordinator = mockk<ContactCoordinator>(relaxed = true),
        userManager = userManager,
        featureFlags = mockk<FeatureFlagController>(relaxed = true),
        blocklist = blocklist,
        profiles = profiles,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        resources = mockk<ResourceHelper>(relaxed = true),
        chatCoordinator = chatCoordinator,
        tipPaymentDelegate = tipPaymentDelegate,
        e2eePolicy = mockk<E2eePolicy>(relaxed = true),
        startChattingPayer = payer,
        chatController = chatController,
        featuredGroupsStore = featuredGroupsStore,
    )

    private fun openFull(model: ChatProfileViewModel) =
        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(serverJoin), isFullProfile = true))

    private fun participant(
        joinedAt: Instant?,
        displayName: String = "Sally",
        id: List<Byte> = theirId,
    ) = ChatParticipant.TipUser(
        userId = id,
        profile = UserProfile(
            displayName = displayName,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            joinedAt = joinedAt,
            userId = id,
            username = "sally_streamer",
        ),
    )

    @Test
    fun `a chat's cached participant fetches the join date`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertEquals(serverJoin, model.stateFlow.value.joinDate)
        coVerify(exactly = 1) { profiles.getProfileForUser(theirId) }
    }

    @Test
    fun `a cached participant with no name takes the server's`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val model = viewModel()

        model.dispatchEvent(
            ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin, displayName = ""))
        )

        assertEquals("Sally", model.stateFlow.value.participant?.name)
    }

    private fun featured(hex: String) = ChatMetadata(
        chatId = ChatId(hex),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `a person's featured groups are read by their handle`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val groups = listOf(featured("aa"), featured("bb"))
        coEvery { chatController.getFeaturedGroups("sally_streamer") } returns Result.success(groups)
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertEquals(groups, model.stateFlow.value.featuredGroups)
    }

    @Test
    fun `a failed featured groups read leaves the section hidden`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        coEvery { chatController.getFeaturedGroups(any()) } returns Result.failure(Exception("offline"))
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertEquals(emptyList(), model.stateFlow.value.featuredGroups)
    }

    @Test
    fun `a failed fetch keeps the cached participant`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns Result.failure(Exception("offline"))
        val model = viewModel()

        model.dispatchEvent(
            ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin, displayName = "Cached"))
        )

        assertEquals("Cached", model.stateFlow.value.participant?.name)
        assertEquals(cachedJoin, model.stateFlow.value.joinDate)
    }

    @Test
    fun `a freshly fetched profile keeps its join date without fetching again`() = runTest {
        val model = viewModel()

        model.dispatchEvent(
            ChatProfileViewModel.Event.OnParticipantSet(participant(serverJoin), isFullProfile = true)
        )

        assertEquals(serverJoin, model.stateFlow.value.joinDate)
        coVerify(exactly = 0) { profiles.getProfileForUser(any()) }
    }

    @Test
    fun `a partial participant fetches the full profile and marks it loaded`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertTrue(model.stateFlow.value.isFullProfileLoaded)
    }

    @Test
    fun `opening the same person again from the same flow settles again`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val model = viewModel()
        val cached = ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin))

        // The chat's flow keeps one view model, so a second tap on the same avatar re-sets the
        // same participant. The reducer resets the settled flag; the fetch has to run again.
        model.dispatchEvent(cached)
        model.dispatchEvent(cached)

        assertTrue(model.stateFlow.value.profileSettled)
        assertTrue(model.stateFlow.value.isFullProfileLoaded)
        coVerify(exactly = 2) { profiles.getProfileForUser(theirId) }
    }

    @Test
    fun `a partial participant is not full until the fetch lands`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns Result.failure(Exception("offline"))
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertFalse(model.stateFlow.value.isFullProfileLoaded)
    }

    @Test
    fun `pinned action goes from start chatting to open chat once members appear`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        val model = viewModel()
        assertNull(model.stateFlow.value.pinnedAction)

        openFull(model)
        assertEquals(ProfilePinnedAction.StartChatting(Fiat(1, CurrencyCode.USD)), model.stateFlow.value.pinnedAction)

        members.value = listOf(mockk<ChatMember>())
        assertEquals(ProfilePinnedAction.OpenChat, model.stateFlow.value.pinnedAction)
    }

    @Test
    fun `blocked beats an existing dm`() = runTest {
        members.value = listOf(mockk<ChatMember>())
        blocked.value = true
        val model = viewModel()

        openFull(model)

        assertEquals(ProfilePinnedAction.Unblock, model.stateFlow.value.pinnedAction)
    }

    @Test
    fun `no fee yet pins no button, and a tap does nothing`() = runTest {
        val model = viewModel()
        openFull(model)
        assertNull(model.stateFlow.value.pinnedAction)

        model.eventFlow.test {
            model.dispatchEvent(ChatProfileViewModel.Event.PinnedActionTapped)
            assertIs<ChatProfileViewModel.Event.PinnedActionTapped>(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(model.stateFlow.value.paymentSheetVisible)
    }

    @Test
    fun `tapping start chatting with a fee opens the sheet`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        val model = viewModel()
        openFull(model)

        model.dispatchEvent(ChatProfileViewModel.Event.PinnedActionTapped)

        assertTrue(model.stateFlow.value.paymentSheetVisible)
    }

    @Test
    fun `a payment whose dm appears opens the chat`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)

        model.eventFlow.test {
            model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
            members.value = listOf(mockk<ChatMember>())
            var item = awaitItem()
            while (item !is ChatProfileViewModel.Event.OpenChat) item = awaitItem()
            assertEquals(dmChatId, item.chatId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a payment whose dm never appears stays on the profile`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)
        val seen = mutableListOf<ChatProfileViewModel.Event>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { model.eventFlow.toList(seen) }

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(11_000)

        assertTrue(seen.none { it is ChatProfileViewModel.Event.OpenChat })
        job.cancel()
    }

    @Test
    fun `unblock failing calls through to the blocklist`() = runTest {
        coEvery { blocklist.unblock(any()) } returns Result.failure(Exception("nope"))
        val model = viewModel()
        openFull(model)

        model.dispatchEvent(ChatProfileViewModel.Event.Unblock)

        coVerify(exactly = 1) { blocklist.unblock(theirId) }
    }

    @Test
    fun `other events are not stalled while a paid chat waits for its members`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        coEvery { blocklist.unblock(any()) } returns Result.success(Unit)
        val model = viewModel()
        openFull(model)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(1_000)
        model.dispatchEvent(ChatProfileViewModel.Event.Unblock)

        coVerify(exactly = 1) { blocklist.unblock(theirId) }
    }

    @Test
    fun `a paid chat waiting for its members cannot be paid for again`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(1_000)
        // The sheet's own show and dismiss both reset the sheet's progress; neither may reopen payment.
        model.dispatchEvent(ChatProfileViewModel.Event.PinnedActionTapped)
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        model.dispatchEvent(ChatProfileViewModel.Event.DismissPaymentSheet)
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(11_000)

        coVerify(exactly = 1) { payer.pay(any(), any(), any()) }
        assertEquals(ProfilePinnedAction.OpeningChat, model.stateFlow.value.pinnedAction)
    }

    @Test
    fun `the sheet stays up and locked after a successful payment until the members arrive`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(5_000)

        assertTrue(model.stateFlow.value.paymentSheetVisible)
        assertTrue(model.stateFlow.value.paymentInProgress)
        assertTrue(model.stateFlow.value.sendProgress.success)
    }

    @Test
    fun `dismissing the sheet is ignored while a payment is out`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(1_000)

        model.dispatchEvent(ChatProfileViewModel.Event.DismissPaymentSheet)

        assertTrue(model.stateFlow.value.paymentSheetVisible)
        assertTrue(model.stateFlow.value.sendProgress.success)
    }

    @Test
    fun `members arriving hides the sheet and opens the chat`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)
        val seen = mutableListOf<ChatProfileViewModel.Event>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { model.eventFlow.toList(seen) }
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(2_000)
        assertTrue(model.stateFlow.value.paymentSheetVisible)

        members.value = listOf(mockk<ChatMember>())
        advanceTimeBy(1_000)

        assertFalse(model.stateFlow.value.paymentSheetVisible)
        assertFalse(model.stateFlow.value.paymentInProgress)
        assertTrue(seen.any { it is ChatProfileViewModel.Event.OpenChat })
        job.cancel()
    }

    @Test
    fun `members never arriving releases the sheet and stays on the profile`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.success(dmChatId)
        val model = viewModel()
        openFull(model)
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        advanceTimeBy(11_000)

        assertFalse(model.stateFlow.value.paymentSheetVisible)
        assertFalse(model.stateFlow.value.paymentInProgress)
        assertEquals(ProfilePinnedAction.OpeningChat, model.stateFlow.value.pinnedAction)
    }

    @Test
    fun `a failed payment leaves the sheet dismissable`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.failure(Exception("nope"))
        val model = viewModel()
        openFull(model)
        model.dispatchEvent(ChatProfileViewModel.Event.ShowPaymentSheet)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        assertFalse(model.stateFlow.value.paymentInProgress)
        model.dispatchEvent(ChatProfileViewModel.Event.DismissPaymentSheet)

        assertFalse(model.stateFlow.value.paymentSheetVisible)
    }

    @Test
    fun `a failed payment can be tried again`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        coEvery { payer.pay(any(), any(), any()) } returns Result.failure(Exception("nope"))
        val model = viewModel()
        openFull(model)

        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)

        coVerify(exactly = 2) { payer.pay(any(), any(), any()) }
        assertTrue(model.stateFlow.value.pinnedAction is ProfilePinnedAction.StartChatting)
    }

    @Test
    fun `a generic payment failure is reported, a blocked one is not`() = runTest {
        fee.value = Fiat(1, CurrencyCode.USD)
        val model = viewModel()
        openFull(model)
        com.getcode.manager.BottomBarManager.clear()

        coEvery { payer.pay(any(), any(), any()) } returns
            Result.failure(StartChattingPayer.PaymentBlocked())
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        assertTrue(com.getcode.manager.BottomBarManager.messages.value.isEmpty())

        coEvery { payer.pay(any(), any(), any()) } returns Result.failure(Exception("nope"))
        model.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting)
        assertEquals(1, com.getcode.manager.BottomBarManager.messages.value.size)
        com.getcode.manager.BottomBarManager.clear()
    }

    // The reducer, for the flow-shared view model that is reused across people.

    private fun reduce(state: ChatProfileViewModel.State, event: ChatProfileViewModel.Event) =
        ChatProfileViewModel.updateStateForEvent(event)(state)

    private val otherId: List<Byte> = List(16) { 3 }

    @Test
    fun `opening a different person clears everything that was the last one's`() {
        val dirty = ChatProfileViewModel.State(
            selfId = List(16) { 1 },
            participant = participant(serverJoin),
            joinDate = serverJoin,
            isFullProfileLoaded = true,
            profileSettled = true,
            dmChatId = dmChatId,
            dmExists = true,
            isBlocked = true,
            isMuted = true,
            fee = Fiat(1, CurrencyCode.USD),
            isEncrypted = true,
            paymentSheetVisible = true,
            sendProgress = com.getcode.view.LoadingSuccessState(loading = true),
        )

        val next = reduce(
            dirty,
            ChatProfileViewModel.Event.OnParticipantSet(participant(null, "Bo", otherId)),
        )

        assertEquals(otherId, (next.participant as ChatParticipant.TipUser).userId)
        assertNull(next.joinDate)
        assertFalse(next.isFullProfileLoaded)
        assertFalse(next.profileSettled)
        assertNull(next.dmChatId)
        assertFalse(next.dmExists)
        assertFalse(next.isBlocked)
        assertFalse(next.isMuted)
        assertNull(next.fee)
        assertFalse(next.isEncrypted)
        assertFalse(next.paymentSheetVisible)
        assertTrue(next.sendProgress.isIdle)
        assertEquals(dirty.selfId, next.selfId)
    }

    @Test
    fun `opening a different person keeps what they have paid for`() {
        val paid = ChatProfileViewModel.State(
            participant = participant(serverJoin),
            paidRecipients = setOf(theirId),
        )

        val next = reduce(
            paid,
            ChatProfileViewModel.Event.OnParticipantSet(participant(null, "Bo", otherId)),
        )

        assertEquals(setOf(theirId), next.paidRecipients)
    }

    @Test
    fun `opening the same person again keeps the profile settled`() {
        val settled = ChatProfileViewModel.State(
            participant = participant(serverJoin),
            joinDate = serverJoin,
            isFullProfileLoaded = true,
            profileSettled = true,
            dmExists = true,
            fee = Fiat(1, CurrencyCode.USD),
        )

        val next = reduce(
            settled,
            ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)),
        )

        assertTrue(next.isFullProfileLoaded)
        assertTrue(next.profileSettled)
        assertEquals(serverJoin, next.joinDate)
        assertTrue(next.dmExists)
        // The server's profile is not swapped back for the cached one the host handed over.
        assertEquals(serverJoin, (next.participant as ChatParticipant.TipUser).profile.joinedAt)
    }

    @Test
    fun `a profile that arrives for the person who was left is ignored`() {
        val onBo = ChatProfileViewModel.State(participant = participant(null, "Bo", otherId))

        val next = reduce(
            onBo,
            ChatProfileViewModel.Event.ProfileLoaded(participant(serverJoin)),
        )

        assertEquals(onBo, next)
    }
}
