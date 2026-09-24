package com.flipcash.app.tipping.internal

import androidx.compose.foundation.text.input.TextFieldState
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.GroupAccess
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the form decides before it calls `StartChat` — nodes 10127:118014 through 10127:118237.
 *
 * Two of those decisions are contractual rather than cosmetic. The rules the draft describes must be
 * a single *listener* minimum balance, naming either no mint (All Currencies — every holding added
 * up) or exactly one; and the creator has to satisfy the rule they are setting, measured by the same
 * predicate that will gate everyone else, or the server answers `RULES_NOT_SATISFIED` on a group
 * they just made.
 */
class CreateGroupStateTest {

    private fun mint(seed: Byte) = Mint(ByteArray(32) { seed }.toList())

    private fun token(seed: Byte, symbol: String) = MintMetadata(
        address = mint(seed),
        decimals = 6,
        name = symbol,
        symbol = symbol,
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
    )

    private fun held(seed: Byte, symbol: String, dollars: Double) =
        TokenWithBalance(token = token(seed, symbol), balance = Fiat(dollars))

    private val badBoys = mint(1)

    private fun state(
        title: String = "Ballers",
        currency: GroupCurrency = GroupCurrency.Specific(badBoys),
        amount: Fiat? = Fiat(100),
        balances: List<TokenWithBalance> = listOf(held(1, "BadBoys", 250.0)),
    ) = CreateGroupViewModel.State(
        titleFieldState = TextFieldState(title),
        balances = balances,
        currency = currency,
        amount = amount,
    )

    private fun CreateGroupViewModel.State.on(event: CreateGroupViewModel.Event) =
        CreateGroupViewModel.updateStateForEvent(event)(this)

    @Test
    fun `the form opens on all currencies, and balances arriving do not move it`() {
        // Node 10364:1059. Seating the largest holding instead would set a narrower rule than the
        // creator asked for, and one they did not pick.
        val opened = CreateGroupViewModel.State()
            .on(CreateGroupViewModel.Event.OnBalancesChanged(listOf(held(1, "BadBoys", 250.0))))

        assertEquals(GroupCurrency.All, opened.currency)
        assertNull(opened.token)
        assertNull(opened.currencyName)
    }

    @Test
    fun `picking a token replaces all currencies, and picking all replaces the token`() {
        val picked = CreateGroupViewModel.State()
            .on(CreateGroupViewModel.Event.OnCurrencySelected(GroupCurrency.Specific(badBoys)))
        assertEquals(GroupCurrency.Specific(badBoys), picked.currency)

        val back = picked.on(CreateGroupViewModel.Event.OnCurrencySelected(GroupCurrency.All))
        assertEquals(GroupCurrency.All, back.currency)
    }

    @Test
    fun `the draft becomes one listener minimum balance in one mint`() {
        val rules = state().rules

        assertEquals(emptyList(), rules?.speaker)
        assertEquals(1, rules?.listener?.size)

        val requirement = assertIs<ChatRuleRequirement.MinimumBalance>(rules?.listener?.first())
        assertEquals(Fiat(100), requirement.amount)
        assertEquals(listOf(badBoys), requirement.mints)
    }

    @Test
    fun `an all currencies draft names no mint`() {
        val rules = state(currency = GroupCurrency.All).rules

        assertEquals(emptyList(), rules?.speaker)
        val requirement = assertIs<ChatRuleRequirement.MinimumBalance>(rules?.listener?.single())
        assertEquals(Fiat(100), requirement.amount)
        assertEquals(emptyList(), requirement.mints)
    }

    @Test
    fun `an incomplete draft describes no rules and cannot be created`() {
        assertNull(state(amount = null).rules)
        assertNull(state(currency = GroupCurrency.All, amount = null).rules)
        assertFalse(state(amount = null).canCreate)
        assertFalse(state(title = "  ").canCreate)
    }

    @Test
    fun `a creator who holds the requirement can create`() {
        val state = state(balances = listOf(held(1, "BadBoys", 100.0)))

        assertIs<GroupAccess.Eligible>(state.access)
        assertTrue(state.selfSatisfied)
        assertTrue(state.canCreate)
    }

    @Test
    fun `a creator short of their own requirement cannot`() {
        val state = state(balances = listOf(held(1, "BadBoys", 99.0)))

        assertIs<GroupAccess.Blocked>(state.access)
        assertFalse(state.selfSatisfied)
        assertFalse(state.canCreate)
    }

    @Test
    fun `balance in another mint does not satisfy a rule naming this one`() {
        val state = state(
            balances = listOf(held(1, "BadBoys", 10.0), held(2, "Other", 5_000.0)),
        )

        assertFalse(state.selfSatisfied)
        assertFalse(state.canCreate)
    }

    @Test
    fun `an all currencies rule is met by holdings added up`() {
        // $60 + $50 meets $100 though neither does alone — the server sums every holding in USD.
        val state = state(
            currency = GroupCurrency.All,
            balances = listOf(held(1, "BadBoys", 60.0), held(2, "Other", 50.0)),
        )

        assertEquals(Fiat(110.0), state.totalBalance)
        assertIs<GroupAccess.Eligible>(state.access)
        assertTrue(state.canCreate)
        assertEquals(true, state.allCurrenciesSatisfied)
    }

    @Test
    fun `an all currencies rule the total falls short of blocks create`() {
        val state = state(
            currency = GroupCurrency.All,
            amount = Fiat(500),
            balances = listOf(held(1, "BadBoys", 60.0), held(2, "Other", 50.0)),
        )

        assertIs<GroupAccess.Blocked>(state.access)
        assertFalse(state.canCreate)
        assertEquals(false, state.allCurrenciesSatisfied)
    }

    @Test
    fun `the header card measures the total even while a token is picked`() {
        // Node 10372:1015: the card still says whether All would meet the amount.
        val state = state(balances = listOf(held(1, "BadBoys", 10.0), held(2, "Other", 95.0)))

        assertFalse(state.selfSatisfied)
        assertEquals(true, state.allCurrenciesSatisfied)
        assertNull(state(amount = null).allCurrenciesSatisfied)
    }
}
