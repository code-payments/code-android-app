package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.services.models.SetGroupMinimumBalanceError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.robolectric.annotation.Config

/**
 * The requirement editor enters in the preferred currency and stores USD, keeping the replaced
 * rule's mints. Until the contract can change rules every save fails as unavailable, which is
 * announced and not reported; any other failure is both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditGroupBalanceRequirementViewModelTest {

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val chatId = ChatId(ByteArray(32) { 9 }.toList())
    private val mint = PublicKey(ByteArray(32) { 4 }.toList())

    // Two euros to the dollar keeps the arithmetic readable: $10 seeds as 20, 30 saves as $15.
    private val euroRate = Rate(fx = 2.0, currency = CurrencyCode.EUR)

    private val scheduler = TestCoroutineScheduler()
    private val reported = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun model(
        current: ChatRuleRequirement.MinimumBalance?,
        rate: Rate = euroRate,
        role: GroupBalanceRole = GroupBalanceRole.Join,
    ): EditGroupBalanceRequirementViewModel {
        val exchange = mockk<Exchange>(relaxed = true) {
            every { preferredRate } returns rate
            every { observePreferredRate() } returns flowOf(rate)
            every { getCurrency(any()) } returns Currency(code = rate.currency.name, name = "", fractionUnits = 2)
            every { rateFor(any()) } returns null
        }
        return EditGroupBalanceRequirementViewModel(
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(scheduler)),
            exchange = exchange,
            chatCoordinator = chatCoordinator,
            resources = resources,
        ).apply {
            reportError = { reported += it }
            dispatchEvent(EditGroupBalanceRequirementViewModel.Event.Initialize(chatId, role, current))
        }
    }

    private fun usd(amount: Double, mints: List<PublicKey> = listOf(mint)) =
        ChatRuleRequirement.MinimumBalance(Fiat(amount, CurrencyCode.USD), mints)

    private fun EditGroupBalanceRequirementViewModel.type(vararg digits: Int) {
        digits.forEach(amountDelegate::onNumber)
    }

    private fun EditGroupBalanceRequirementViewModel.clearEntry() {
        repeat(10) { amountDelegate.onBackspace() }
    }

    /** Save, then the prompt's confirm — the only path that writes. */
    private fun EditGroupBalanceRequirementViewModel.saveAndConfirm() {
        dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        scheduler.advanceUntilIdle()
        val prompt = BottomBarManager.messages.value.last()
        prompt.actions.first().onClick()
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `seeds the current rule in the entry currency`() = runTest(scheduler) {
        val model = model(current = usd(10.0))
        advanceUntilIdle()

        assertEquals(20.0, model.amountDelegate.state.value.enteredAmount, 0.0001)
        // Unchanged from what is stored, so there is nothing to save yet.
        assertFalse(model.canSave.value)
    }

    @Test
    fun `a group without the rule starts empty and cannot save`() = runTest(scheduler) {
        val model = model(current = null)
        advanceUntilIdle()

        assertEquals(0.0, model.amountDelegate.state.value.enteredAmount, 0.0)
        assertFalse(model.canSave.value)
    }

    @Test
    fun `save is enabled only for a positive amount that differs from the rule`() = runTest(scheduler) {
        val model = model(current = usd(10.0))
        advanceUntilIdle()

        model.clearEntry()
        model.type(0)
        advanceUntilIdle()
        assertFalse("zero", model.canSave.value)

        model.clearEntry()
        model.type(2, 0)
        advanceUntilIdle()
        assertFalse("same as the rule", model.canSave.value)

        model.clearEntry()
        model.type(3, 0)
        advanceUntilIdle()
        assertTrue("changed", model.canSave.value)
    }

    @Test
    fun `save stays disabled without a usable rate`() = runTest(scheduler) {
        val model = model(current = usd(10.0), rate = Rate.ignore)
        advanceUntilIdle()

        assertEquals("nothing to seed from", 0.0, model.amountDelegate.state.value.enteredAmount, 0.0)
        model.type(5)
        advanceUntilIdle()
        assertFalse(model.canSave.value)
    }

    @Test
    fun `saving converts to USD and keeps the rule's mints`() = runTest(scheduler) {
        val sent = slot<ChatRuleRequirement.MinimumBalance>()
        coEvery { chatCoordinator.setMinimumBalance(chatId, GroupBalanceRole.Chat, capture(sent)) } returns
            Result.failure(SetGroupMinimumBalanceError.Unavailable())
        val model = model(current = usd(10.0), role = GroupBalanceRole.Chat)
        advanceUntilIdle()

        model.clearEntry()
        model.type(3, 0)
        advanceUntilIdle()
        model.saveAndConfirm()

        assertEquals(CurrencyCode.USD, sent.captured.amount.currencyCode)
        assertEquals(15.0, sent.captured.amount.decimalValue, 0.0001)
        assertEquals(listOf(mint), sent.captured.mints)
    }

    @Test
    fun `a new rule names no mint`() = runTest(scheduler) {
        val sent = slot<ChatRuleRequirement.MinimumBalance>()
        coEvery { chatCoordinator.setMinimumBalance(chatId, GroupBalanceRole.Join, capture(sent)) } returns
            Result.failure(SetGroupMinimumBalanceError.Unavailable())
        val model = model(current = null)
        advanceUntilIdle()

        model.type(5)
        advanceUntilIdle()
        model.saveAndConfirm()

        assertEquals(2.5, sent.captured.amount.decimalValue, 0.0001)
        assertEquals(emptyList<PublicKey>(), sent.captured.mints)
    }

    @Test
    fun `save asks before it sends`() = runTest(scheduler) {
        val model = model(current = null)
        advanceUntilIdle()
        model.type(5)
        advanceUntilIdle()

        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.DESTRUCTIVE, BottomBarManager.messages.value.last().type)
        coVerify(exactly = 0) { chatCoordinator.setMinimumBalance(any(), any(), any()) }
    }

    @Test
    fun `an unavailable save is announced but not reported`() = runTest(scheduler) {
        coEvery { chatCoordinator.setMinimumBalance(any(), any(), any()) } returns
            Result.failure(SetGroupMinimumBalanceError.Unavailable())
        val model = model(current = null)
        advanceUntilIdle()
        model.type(5)
        advanceUntilIdle()

        model.saveAndConfirm()

        assertEquals(BottomBarManager.BottomBarMessageType.INFO, BottomBarManager.messages.value.last().type)
        assertTrue(reported.isEmpty())
        assertTrue(model.stateFlow.value.processingState.isIdle)
    }

    @Test
    fun `any other failure is reported and shown as an error`() = runTest(scheduler) {
        val failure = IllegalStateException("boom")
        coEvery { chatCoordinator.setMinimumBalance(any(), any(), any()) } returns Result.failure(failure)
        val model = model(current = null)
        advanceUntilIdle()
        model.type(5)
        advanceUntilIdle()

        model.saveAndConfirm()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertEquals(listOf<Throwable>(failure), reported)
    }
}

