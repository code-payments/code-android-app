package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
 * The requirement editor enters in the preferred currency and saves the group's whole rule set
 * through `EditChatRequest.rules`, with only the edited minimum changed and stored in the listener
 * rule's currency. What the server would refuse is stopped before a request where it can be known.
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
    private val usdRate = Rate(fx = 1.0, currency = CurrencyCode.USD)
    private val yenRate = Rate(fx = 150.0, currency = CurrencyCode.JPY)

    private val scheduler = TestCoroutineScheduler()
    private val reported = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        BottomBarManager.clear()
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.success(mockk<ChatMetadata>(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun model(
        rules: ChatRules?,
        role: GroupBalanceRole = GroupBalanceRole.Join,
        rate: Rate = euroRate,
        rates: Map<CurrencyCode, Rate> = mapOf(CurrencyCode.EUR to euroRate, CurrencyCode.JPY to yenRate),
        balances: List<TokenWithBalance>? = null,
    ): EditGroupBalanceRequirementViewModel {
        val exchange = mockk<Exchange>(relaxed = true) {
            every { preferredRate } returns rate
            every { observePreferredRate() } returns flowOf(rate)
            every { observeRates() } returns flowOf(rates)
            every { getCurrency(any()) } returns Currency(code = rate.currency.name, name = "", fractionUnits = 2)
            every { rateFor(any()) } returns null
        }
        val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
            every { tokenBalances } returns (balances?.let { flowOf(it) } ?: emptyFlow())
        }
        return EditGroupBalanceRequirementViewModel(
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(scheduler)),
            exchange = exchange,
            chatCoordinator = chatCoordinator,
            tokenCoordinator = tokenCoordinator,
            resources = resources,
        ).apply {
            reportError = { reported += it }
            dispatchEvent(EditGroupBalanceRequirementViewModel.Event.Initialize(chatId, role, rules))
        }
    }

    private fun minimum(amount: Double, currency: CurrencyCode = CurrencyCode.USD, mints: List<PublicKey> = listOf(mint)) =
        ChatRuleRequirement.MinimumBalance(Fiat(amount, currency), mints)

    private fun rules(
        join: ChatRuleRequirement.MinimumBalance = minimum(10.0),
        chat: ChatRuleRequirement.MinimumBalance? = null,
        listenerExtra: List<ChatRuleRequirement> = emptyList(),
        speakerExtra: List<ChatRuleRequirement> = emptyList(),
    ) = ChatRules(
        listener = listOf(join) + listenerExtra,
        speaker = listOfNotNull(chat) + speakerExtra,
    )

    private fun held(dollars: Double) = TokenWithBalance(
        token = MintMetadata(
            address = Mint(mint.bytes),
            decimals = 6,
            name = "T",
            symbol = "T",
            createdAt = null,
            description = "",
            imageUrl = "",
            vmMetadata = VmMetadata(
                vm = PublicKey.fromBase58("11111111111111111111111111111111"),
                authority = PublicKey.fromBase58("11111111111111111111111111111111"),
                lockDurationInDays = 21,
            ),
            launchpadMetadata = null,
            billCustomizations = null,
            socialLinks = emptyList(),
            holderMetrics = HolderMetrics.None,
        ),
        balance = Fiat(dollars),
    )

    private fun EditGroupBalanceRequirementViewModel.type(vararg digits: Int) {
        digits.forEach(amountDelegate::onNumber)
    }

    private fun EditGroupBalanceRequirementViewModel.clearEntry() {
        repeat(10) { amountDelegate.onBackspace() }
    }

    private fun EditGroupBalanceRequirementViewModel.retype(vararg digits: Int) {
        clearEntry()
        type(*digits)
        scheduler.advanceUntilIdle()
    }

    /** Save, then the prompt's confirm — the only path that writes. */
    private fun EditGroupBalanceRequirementViewModel.saveAndConfirm() {
        dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        scheduler.advanceUntilIdle()
        BottomBarManager.messages.value.last().actions.first().onClick()
        scheduler.advanceUntilIdle()
    }

    private fun sentRules(): ChatRules {
        val sent = slot<EditChatParameters>()
        coVerify { chatCoordinator.editChat(chatId, capture(sent)) }
        return checkNotNull(sent.captured.rules)
    }

    private fun assertNothingSent() {
        coVerify(exactly = 0) { chatCoordinator.editChat(any(), any()) }
    }

    @Test
    fun `seeds the current rule in the entry currency`() = runTest(scheduler) {
        val model = model(rules())
        advanceUntilIdle()

        assertEquals(20.0, model.amountDelegate.state.value.enteredAmount, 0.0001)
        // Unchanged from what is stored, so there is nothing to save yet.
        assertFalse(model.canSave.value)
    }

    @Test
    fun `an untouched rule in another currency still reads as unchanged`() = runTest(scheduler) {
        // ¥1000 seeds as €13.33, which restates as ¥999.75 and rounds back to ¥1000 only by luck;
        // the seeded entry itself is what has to read as unchanged.
        val model = model(rules(join = minimum(1000.0, CurrencyCode.JPY)))
        advanceUntilIdle()

        assertEquals(13.33, model.amountDelegate.state.value.enteredAmount, 0.0001)
        assertFalse(model.canSave.value)
    }

    @Test
    fun `a group without a Chat minimum starts empty and has nothing to remove`() = runTest(scheduler) {
        val model = model(rules(), role = GroupBalanceRole.Chat)
        advanceUntilIdle()

        assertEquals(0.0, model.amountDelegate.state.value.enteredAmount, 0.0)
        assertFalse(model.canSave.value)
        assertFalse(model.stateFlow.value.canRemove)
    }

    @Test
    fun `save is enabled only for a positive amount that differs from the rule`() = runTest(scheduler) {
        val model = model(rules())
        advanceUntilIdle()

        model.retype(0)
        assertFalse("zero", model.canSave.value)

        model.retype(2, 0)
        assertFalse("same as the rule", model.canSave.value)

        model.retype(3, 0)
        assertTrue("changed", model.canSave.value)
    }

    @Test
    fun `an amount below the minimum transfer value cannot be saved`() = runTest(scheduler) {
        // €0.01 is half a cent, a quarter of a yen at ¥50 to the dollar: less than ¥1.
        val model = model(
            rules(join = minimum(1000.0, CurrencyCode.JPY)),
            rates = mapOf(CurrencyCode.EUR to euroRate, CurrencyCode.JPY to Rate(fx = 50.0, currency = CurrencyCode.JPY)),
        )
        advanceUntilIdle()

        model.clearEntry()
        model.type(0)
        model.amountDelegate.onDecimal()
        model.type(0, 1)
        advanceUntilIdle()

        assertFalse(model.canSave.value)
    }

    @Test
    fun `save stays disabled without a usable rate`() = runTest(scheduler) {
        val model = model(rules(), rate = Rate.ignore)
        advanceUntilIdle()

        assertEquals("nothing to seed from", 0.0, model.amountDelegate.state.value.enteredAmount, 0.0)
        model.type(5)
        advanceUntilIdle()
        assertFalse(model.canSave.value)
    }

    @Test
    fun `a Join change keeps its currency, its mints and every other rule`() = runTest(scheduler) {
        val speaker = minimum(40.0)
        val model = model(
            rules(
                chat = speaker,
                listenerExtra = listOf(ChatRuleRequirement.Staff),
                speakerExtra = listOf(ChatRuleRequirement.Creator),
            )
        )
        advanceUntilIdle()

        model.retype(3, 0)
        model.saveAndConfirm()

        val sent = sentRules()
        assertEquals(listOf(minimum(15.0), ChatRuleRequirement.Staff), sent.listener)
        assertEquals(listOf(speaker, ChatRuleRequirement.Creator), sent.speaker)
    }

    @Test
    fun `a new Chat minimum takes the listener's currency and mints`() = runTest(scheduler) {
        val listenerMints = listOf(mint, PublicKey(ByteArray(32) { 5 }.toList()))
        val model = model(
            rules(join = minimum(1000.0, CurrencyCode.JPY, listenerMints), speakerExtra = listOf(ChatRuleRequirement.Staff)),
            role = GroupBalanceRole.Chat,
        )
        advanceUntilIdle()

        // €30 is $15, which is ¥2250.
        model.type(3, 0)
        advanceUntilIdle()
        model.saveAndConfirm()

        val sent = sentRules()
        assertEquals(listOf(minimum(1000.0, CurrencyCode.JPY, listenerMints)), sent.listener)
        assertEquals(listOf(ChatRuleRequirement.Staff, minimum(2250.0, CurrencyCode.JPY, listenerMints)), sent.speaker)
    }

    @Test
    fun `a Chat minimum at or below Join is blocked before sending`() = runTest(scheduler) {
        val model = model(rules(), role = GroupBalanceRole.Chat, rate = usdRate, rates = emptyMap())
        advanceUntilIdle()

        model.type(1, 0)
        advanceUntilIdle()
        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertNothingSent()
    }

    @Test
    fun `a Chat minimum one cent above Join is accepted`() = runTest(scheduler) {
        val model = model(rules(), role = GroupBalanceRole.Chat, rate = usdRate, rates = emptyMap())
        advanceUntilIdle()

        model.type(1, 0)
        model.amountDelegate.onDecimal()
        model.type(0, 1)
        advanceUntilIdle()
        model.saveAndConfirm()

        assertEquals(listOf(minimum(10.01)), sentRules().speaker)
    }

    @Test
    fun `a Join minimum at or above Chat is blocked before sending`() = runTest(scheduler) {
        val model = model(rules(chat = minimum(20.0)), rate = usdRate, rates = emptyMap())
        advanceUntilIdle()

        model.retype(2, 0)
        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertNothingSent()
    }

    @Test
    fun `removing the Chat minimum sends the rules without it`() = runTest(scheduler) {
        val model = model(rules(chat = minimum(20.0), speakerExtra = listOf(ChatRuleRequirement.Creator)), role = GroupBalanceRole.Chat)
        advanceUntilIdle()
        assertTrue(model.stateFlow.value.canRemove)

        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.RemoveClicked)
        advanceUntilIdle()
        BottomBarManager.messages.value.last().actions.first().onClick()
        advanceUntilIdle()

        val sent = sentRules()
        assertEquals(listOf(minimum(10.0)), sent.listener)
        assertEquals(listOf(ChatRuleRequirement.Creator), sent.speaker)
    }

    @Test
    fun `new minimums the caller does not meet are blocked before sending`() = runTest(scheduler) {
        val model = model(rules(), balances = listOf(held(12.0)))
        advanceUntilIdle()

        // €30 is a $15 Join minimum; the caller holds $12.
        model.retype(3, 0)
        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertNothingSent()
    }

    @Test
    fun `a Chat minimum the caller does not meet is blocked too`() = runTest(scheduler) {
        val model = model(rules(), role = GroupBalanceRole.Chat, balances = listOf(held(12.0)))
        advanceUntilIdle()

        model.type(3, 0)
        advanceUntilIdle()
        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertNothingSent()
    }

    @Test
    fun `a caller who meets the new minimums is asked before it sends`() = runTest(scheduler) {
        val model = model(rules(), balances = listOf(held(20.0)))
        advanceUntilIdle()

        model.retype(3, 0)
        model.dispatchEvent(EditGroupBalanceRequirementViewModel.Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(BottomBarManager.BottomBarMessageType.DESTRUCTIVE, BottomBarManager.messages.value.last().type)
        assertNothingSent()
    }

    @Test
    fun `a save leaves once the server takes it`() = runTest(scheduler) {
        val model = model(rules())
        advanceUntilIdle()

        model.retype(3, 0)
        model.saveAndConfirm()

        coVerify(exactly = 1) { chatCoordinator.editChat(chatId, any()) }
        assertTrue(model.stateFlow.value.processingState.isIdle)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `rules the server will not satisfy are announced but not reported`() = runTest(scheduler) {
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.failure(EditChatError.RulesNotSatisfied())
        val model = model(rules())
        advanceUntilIdle()

        model.retype(3, 0)
        model.saveAndConfirm()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertTrue(reported.isEmpty())
        assertTrue(model.stateFlow.value.processingState.isIdle)
    }

    @Test
    fun `invalid rules are announced and reported`() = runTest(scheduler) {
        val failure = EditChatError.InvalidRules()
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.failure(failure)
        val model = model(rules())
        advanceUntilIdle()

        model.retype(3, 0)
        model.saveAndConfirm()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun `any other failure is reported and shown as an error`() = runTest(scheduler) {
        val failure = IllegalStateException("boom")
        coEvery { chatCoordinator.editChat(any(), any()) } returns Result.failure(failure)
        val model = model(rules())
        advanceUntilIdle()

        model.retype(3, 0)
        model.saveAndConfirm()

        assertEquals(BottomBarManager.BottomBarMessageType.ERROR, BottomBarManager.messages.value.last().type)
        assertEquals(listOf<Throwable>(failure), reported)
    }
}
