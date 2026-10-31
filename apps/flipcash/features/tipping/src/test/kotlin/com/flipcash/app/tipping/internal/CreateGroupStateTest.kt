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
 * a single *listener* minimum balance naming exactly one mint; and the creator has to satisfy the rule they are setting, measured by the same
 * predicate that will gate everyone else, or the server answers `RULES_NOT_SATISFIED` on a group
 * they just made.
 */
class CreateGroupStateTest {

    private fun mint(seed: Byte) = Mint(ByteArray(32) { seed }.toList())

    private fun token(seed: Byte, symbol: String, address: Mint = mint(seed)) = MintMetadata(
        address = address,
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

    private val usdf = TokenWithBalance(
        token = token(0, "USDF", address = Mint.usdf),
        balance = Fiat(1_000.0),
    )

    private val badBoys = mint(1)

    private fun state(
        title: String = "Ballers",
        currency: Mint? = badBoys,
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
    fun `the form opens on the highest community balance, leaving USDF out`() {
        // Node 12370:76881: the picker's top row, which is the largest holding that isn't dollars.
        val opened = CreateGroupViewModel.State().on(
            CreateGroupViewModel.Event.OnBalancesChanged(
                listOf(usdf, held(1, "BadBoys", 50.0), held(2, "Moony", 250.0))
            )
        )

        assertEquals(mint(2), opened.currency)
        assertEquals("Moony", opened.currencyName)
    }

    @Test
    fun `with only USDF the currency stays unset and create stays off`() {
        val opened = CreateGroupViewModel.State(titleFieldState = TextFieldState("Ballers"), amount = Fiat(100))
            .on(CreateGroupViewModel.Event.OnBalancesChanged(listOf(usdf)))

        assertNull(opened.currency)
        assertNull(opened.rules)
        assertFalse(opened.canCreate)
    }

    @Test
    fun `balances that arrive late still seat the default once`() {
        val opened = CreateGroupViewModel.State()
            .on(CreateGroupViewModel.Event.OnBalancesChanged(emptyList()))
        assertNull(opened.currency)

        val arrived = opened.on(
            CreateGroupViewModel.Event.OnBalancesChanged(listOf(held(1, "BadBoys", 50.0)))
        )
        assertEquals(badBoys, arrived.currency)

        // A later balance overtaking it does not move a currency the form is already showing.
        val overtaken = arrived.on(
            CreateGroupViewModel.Event.OnBalancesChanged(
                listOf(held(1, "BadBoys", 50.0), held(2, "Moony", 900.0))
            )
        )
        assertEquals(badBoys, overtaken.currency)
    }

    @Test
    fun `a pick on the sheet survives balances changing`() {
        val picked = CreateGroupViewModel.State()
            .on(CreateGroupViewModel.Event.OnCurrencySelected(mint(2)))
            .on(
                CreateGroupViewModel.Event.OnBalancesChanged(
                    listOf(held(1, "BadBoys", 900.0), held(2, "Moony", 5.0))
                )
            )

        assertEquals(mint(2), picked.currency)
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
    fun `an incomplete draft describes no rules and cannot be created`() {
        assertNull(state(amount = null).rules)
        assertNull(state(currency = null).rules)
        assertFalse(state(currency = null).canCreate)
        assertFalse(state(amount = null).canCreate)
        assertFalse(state(title = "  ").canCreate)
    }

    @Test
    fun `a title at the 64 code point limit can be created and one over cannot`() {
        val balances = listOf(held(1, "BadBoys", 100.0))

        assertTrue(state(title = "a".repeat(64), balances = balances).canCreate)
        assertFalse(state(title = "a".repeat(65), balances = balances).canCreate)
    }

    @Test
    fun `the title limit counts code points, not UTF-16 units`() {
        val balances = listOf(held(1, "BadBoys", 100.0))

        // 64 emoji are 128 UTF-16 units but 64 code points.
        assertTrue(state(title = "\uD83D\uDE00".repeat(64), balances = balances).canCreate)
        assertFalse(state(title = "\uD83D\uDE00".repeat(65), balances = balances).canCreate)
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
    fun `the currency list drops USDF and keeps every other token in order`() {
        val tokens = listOf(mint(1), Mint.usdf, mint(2))

        assertEquals(listOf(mint(1), mint(2)), tokens.withoutUsdf { it })
    }
}
