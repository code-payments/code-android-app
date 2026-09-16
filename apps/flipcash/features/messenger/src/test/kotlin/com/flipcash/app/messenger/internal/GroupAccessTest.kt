package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The gate, as a table.
 *
 * Membership short-circuits everything: a member is past the rules by definition, and re-evaluating
 * them would blur the transcript of a chat they are already in the moment their balance dipped.
 */
class GroupAccessTest {

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
    private val other = mint(2)

    private fun rules(vararg listener: ChatRuleRequirement) =
        ChatRules(listener = listener.toList(), speaker = emptyList())

    @Test
    fun `a member is in, whatever the rules say`() {
        val access = GroupAccess.evaluate(
            isMember = true,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))),
            balances = emptyList(),
        )

        assertEquals(GroupAccess.Membered, access)
    }

    @Test
    fun `no rules means anyone may join`() {
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(isMember = false, rules = null, balances = emptyList()),
        )
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(isMember = false, rules = rules(), balances = emptyList()),
        )
    }

    @Test
    fun `enough of the named token clears the bar`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))),
            balances = listOf(held(1, "BadBoys", 100.0)),
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `too little of the named token is blocked by that requirement`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))

        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(requirement),
            balances = listOf(held(1, "BadBoys", 99.99)),
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
    }

    @Test
    fun `a balance in the wrong token does not count`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))

        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(requirement),
            balances = listOf(held(2, "Other", 1_000.0)),
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
    }

    @Test
    fun `no named mints accepts any single token`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())),
            balances = listOf(held(2, "Other", 150.0)),
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `several named mints are satisfied by the largest, not the sum`() {
        val requirement =
            ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys, other))

        assertEquals(
            GroupAccess.Blocked(requirement),
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(held(1, "BadBoys", 60.0), held(2, "Other", 60.0)),
            ),
        )
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(held(1, "BadBoys", 40.0), held(2, "Other", 120.0)),
            ),
        )
    }

    @Test
    fun `a staff rule blocks with nothing to buy`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.Staff),
            balances = listOf(held(1, "BadBoys", 1_000.0)),
        )

        assertEquals(GroupAccess.Blocked(ChatRuleRequirement.Staff), access)
    }

    @Test
    fun `the first unmet requirement is the one reported`() {
        val balance = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))

        val access = GroupAccess.evaluate(
            isMember = false,
            rules = ChatRules(
                listener = listOf(balance, ChatRuleRequirement.Staff),
                speaker = emptyList(),
            ),
            balances = emptyList(),
        )

        assertEquals(GroupAccess.Blocked(balance), access)
    }

    @Test
    fun `speaker rules do not gate reading`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = ChatRules(
                listener = emptyList(),
                speaker = listOf(ChatRuleRequirement.MinimumBalance(Fiat(500.0), listOf(badBoys))),
            ),
            balances = emptyList(),
        )

        assertEquals(GroupAccess.Eligible, access)
    }
}
