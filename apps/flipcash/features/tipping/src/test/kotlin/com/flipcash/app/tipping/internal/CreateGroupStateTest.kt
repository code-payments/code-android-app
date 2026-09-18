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
 * a single *listener* minimum balance with exactly one mint, which is all the server accepts today;
 * and the creator has to satisfy the rule they are setting, measured by the same predicate that will
 * gate everyone else, or the server answers `RULES_NOT_SATISFIED` on a group they just made.
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
        mint: Mint? = badBoys,
        amount: Fiat? = Fiat(100),
        balances: List<TokenWithBalance> = listOf(held(1, "BadBoys", 250.0)),
    ) = CreateGroupViewModel.State(
        titleFieldState = TextFieldState(title),
        balances = balances,
        mint = mint,
        amount = amount,
    )

    @Test
    fun `the mint the form opens on is not a mint the creator chose`() {
        val opened = CreateGroupViewModel.updateStateForEvent(
            CreateGroupViewModel.Event.OnBalancesChanged(listOf(held(1, "BadBoys", 250.0)))
        )(CreateGroupViewModel.State())

        // The heading reads "Minimum Balance Required" until the creator picks the currency, and
        // the form always opens with one named — so the mint alone cannot tell the two apart.
        // Node 10127:118057 still asks, with the title typed and the opening mint untouched;
        // node 10127:118194 reads "Balance Requirement" on the mint, before any amount.
        assertEquals(badBoys, opened.mint)
        assertFalse(opened.mintChosen)

        val picked = CreateGroupViewModel.updateStateForEvent(
            CreateGroupViewModel.Event.OnMintSelected(badBoys)
        )(opened)

        assertTrue(picked.mintChosen)
    }

    @Test
    fun `a wallet holding only dust seats no mint`() {
        // The currency sheet filters `TokenPurpose.Balance` on `hasDisplayableValue`, so seating
        // from a wider list would name a mint the picker cannot show and the creator could not get
        // back to. Nothing seated means the row reads "Select Currency" instead.
        val opened = CreateGroupViewModel.updateStateForEvent(
            CreateGroupViewModel.Event.OnBalancesChanged(listOf(held(1, "BadBoys", 0.001)))
        )(CreateGroupViewModel.State())

        assertNull(opened.mint)
        assertFalse(opened.mintChosen)
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
        assertNull(state(mint = null).rules)
        assertNull(state(amount = null).rules)
        assertFalse(state(mint = null).canCreate)
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
}
