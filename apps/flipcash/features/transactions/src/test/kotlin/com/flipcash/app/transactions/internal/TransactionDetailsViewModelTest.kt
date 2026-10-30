package com.flipcash.app.transactions.internal

import com.flipcash.app.blocklist.DmDestinationResolver
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.BoostedReaction
import com.flipcash.services.models.RequestedPayment
import com.getcode.utils.base58
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.shared.transactionhistory.TransactionDetails
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.ResolvedTransaction
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The counterpart action on the details screen follows [DmDestinationResolver]: the profile until a
 * DM with the person exists, the DM after. The screen picks "View Profile" or "View in Chat" from
 * [TransactionDetailsViewModel.State.viewsProfile], so that is what is pinned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailsViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val counterpartyId = List<Byte>(32) { it.toByte() }
    private val entryId = List<Byte>(32) { (it + 1).toByte() }
    private val dmChatId = ChatId(ByteArray(32) { (it + 2).toByte() })

    private val profile = AppRoute.Messaging.Profile(
        ProfileAddress.ById(counterpartyId),
        ProfileOrigin.Transaction,
    )
    private val chat = AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(dmChatId))

    private val feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    private val dmDestinations = mockk<DmDestinationResolver>()

    private fun TestScope.createViewModel(destinations: kotlinx.coroutines.flow.Flow<AppRoute>): TransactionDetailsViewModel {
        val transaction = mockk<ResolvedTransaction>(relaxed = true) {
            every { counterpartyId } returns this@TransactionDetailsViewModelTest.counterpartyId
        }
        every { feedCoordinator.transactionDetails(entryId) } returns flowOf(transaction)
        every {
            dmDestinations.observeDmDestination(counterpartyId, ProfileOrigin.Transaction)
        } returns destinations

        return TransactionDetailsViewModel(
            feedCoordinator = feedCoordinator,
            tokenCoordinator = mockk<TokenCoordinator>(relaxed = true),
            transactionController = mockk(relaxed = true),
            clipboardManager = mockk(relaxed = true),
            toastController = mockk(relaxed = true),
            userManager = mockk<UserManager>(relaxed = true),
            exchange = mockk(relaxed = true),
            resources = FakeResourceHelper(),
            dmDestinations = dmDestinations,
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    @Test
    fun `a counterparty with no DM views the profile`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel(flowOf(profile))
        vm.dispatchEvent(TransactionDetailsViewModel.Event.OnIdProvided(entryId))
        advanceUntilIdle()

        assertEquals(profile, vm.stateFlow.value.counterpartDestination)
        assertTrue(vm.stateFlow.value.viewsProfile)
    }

    @Test
    fun `a counterparty with a DM views it in chat`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel(flowOf(chat))
        vm.dispatchEvent(TransactionDetailsViewModel.Event.OnIdProvided(entryId))
        advanceUntilIdle()

        assertEquals(chat, vm.stateFlow.value.counterpartDestination)
        assertFalse(vm.stateFlow.value.viewsProfile)
    }

    @Test
    fun `the action flips when the resolver re-emits`() = runTest(mainCoroutineRule.dispatcher) {
        val destinations = MutableSharedFlow<AppRoute>(replay = 1)
        destinations.emit(profile)
        val vm = createViewModel(destinations)
        vm.dispatchEvent(TransactionDetailsViewModel.Event.OnIdProvided(entryId))
        advanceUntilIdle()
        assertTrue(vm.stateFlow.value.viewsProfile)

        destinations.emit(chat)
        advanceUntilIdle()
        assertFalse(vm.stateFlow.value.viewsProfile)

        destinations.emit(profile)
        advanceUntilIdle()
        assertTrue(vm.stateFlow.value.viewsProfile)
    }

    @Test
    fun `an entry with no counterparty has no destination`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel(flowOf(chat))
        val none = mockk<ResolvedTransaction>(relaxed = true) { every { counterpartyId } returns null }
        every { feedCoordinator.transactionDetails(entryId) } returns flowOf(none)
        vm.dispatchEvent(TransactionDetailsViewModel.Event.OnIdProvided(entryId))
        advanceUntilIdle()

        assertNull(vm.stateFlow.value.counterpartDestination)
        assertFalse(vm.stateFlow.value.viewsProfile)
    }

    // --- "View Message": the route is read off the stored boost snapshot (D1-D3). ---

    private val boost = BoostedReaction(chatId = dmChatId, messageId = 77L, emoji = Emoji("\uD83D\uDD25"))
    private val boostRoute = AppRoute.Messaging.Chat(
        identifier = ChatIdentifier.ByChatId(dmChatId),
        jumpToMessageId = 77L,
    )

    private fun stateWith(boost: BoostedReaction?, canViewInChat: Boolean): TransactionDetailsViewModel.State {
        val details = mockk<TransactionDetails>(relaxed = true) {
            every { boostedReaction } returns boost
            every { this@mockk.canViewInChat } returns canViewInChat
        }
        val transaction = mockk<ResolvedTransaction>(relaxed = true) { every { this@mockk.details } returns details }
        return TransactionDetailsViewModel.State(transaction = transaction)
    }

    @Test
    fun `D1 a boost with a counterparty opens the boosted message in its chat`() {
        val state = stateWith(boost, canViewInChat = true)
        assertEquals(boostRoute, state.boostedMessageRoute)
    }

    @Test
    fun `D2 a split boost opens the same route with no counterparty`() {
        val state = stateWith(boost, canViewInChat = false)
        assertEquals(boostRoute, state.boostedMessageRoute)
    }

    @Test
    fun `D3 without a boost there is no message route and the chat button is as before`() {
        val state = stateWith(null, canViewInChat = true)
        assertNull(state.boostedMessageRoute)
        assertTrue(state.transaction!!.details.canViewInChat)
    }

    // --- "View Request": the route opens the request's details over its chat. ---

    private val requested = RequestedPayment(chatId = dmChatId, paymentRequestId = List(16) { it.toByte() })

    private fun stateWith(requested: RequestedPayment?, boost: BoostedReaction?): TransactionDetailsViewModel.State {
        val details = mockk<TransactionDetails>(relaxed = true) {
            every { requestedPayment } returns requested
            every { boostedReaction } returns boost
        }
        val transaction = mockk<ResolvedTransaction>(relaxed = true) { every { this@mockk.details } returns details }
        return TransactionDetailsViewModel.State(transaction = transaction)
    }

    @Test
    fun `R1 a requested payment opens its request in its chat`() {
        val route = stateWith(requested, boost = null).requestedPaymentRoute
        assertEquals(
            AppRoute.Messaging.Chat(
                identifier = ChatIdentifier.ByChatId(dmChatId),
                openRequestId = requested.paymentRequestId.base58,
            ),
            route,
        )
    }

    @Test
    fun `R2 a boost wins when both are set`() {
        val state = stateWith(requested, boost)
        assertNull(state.requestedPaymentRoute)
        assertEquals(boostRoute, state.boostedMessageRoute)
    }

    @Test
    fun `R3 a payment with no request has no request route`() {
        assertNull(stateWith(null, null).requestedPaymentRoute)
    }
}
