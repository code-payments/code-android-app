package com.flipcash.app.transactions.internal

import com.flipcash.app.blocklist.DmDestinationResolver
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatId
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
}
