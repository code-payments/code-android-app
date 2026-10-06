package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.ChatCoordinator
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
    )

    private fun openFull(model: ChatProfileViewModel) =
        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(serverJoin), isFullProfile = true))

    private fun participant(joinedAt: Instant?, displayName: String = "Sally") = ChatParticipant.TipUser(
        userId = theirId,
        profile = UserProfile(
            displayName = displayName,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            joinedAt = joinedAt,
            userId = theirId,
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
        assertEquals(ProfilePinnedAction.StartChatting(fee.value), model.stateFlow.value.pinnedAction)

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
    fun `tapping start chatting with no fee asks for the keypad`() = runTest {
        val model = viewModel()
        openFull(model)

        model.eventFlow.test {
            model.dispatchEvent(ChatProfileViewModel.Event.PinnedActionTapped)
            assertIs<ChatProfileViewModel.Event.PinnedActionTapped>(awaitItem())
            assertIs<ChatProfileViewModel.Event.OpenSendCash>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
}
