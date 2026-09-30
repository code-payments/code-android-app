package com.flipcash.shared.chat

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

    /** Exact micro-dollars, for the cases that sit on a rounding edge. */
    private fun heldMicros(seed: Byte, symbol: String, micros: Long) =
        TokenWithBalance(token = token(seed, symbol), balance = Fiat(quarks = micros))

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
            isStaff = false,
        )

        assertEquals(GroupAccess.Membered, access)
    }

    @Test
    fun `no rules means anyone may join`() {
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(
                isMember = false,
                rules = null,
                balances = emptyList(),
                isStaff = false,
            ),
        )
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(),
                balances = emptyList(),
                isStaff = false,
            ),
        )
    }

    @Test
    fun `enough of the named token clears the bar`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))),
            balances = listOf(held(1, "BadBoys", 100.0)),
            isStaff = false,
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
            isStaff = false,
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
    }

    @Test
    fun `a balance that shows as the requirement clears the bar`() {
        // $5 of a launchpad token valued at a supply that lags the buy comes back a fraction
        // short. The wallet shows it as $5.00, so the gate must agree.
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(5.0), listOf(badBoys))),
            balances = listOf(heldMicros(1, "BadBoys", 4_998_000)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `half a cent short rounds up to the requirement`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(5.0), listOf(badBoys))),
            balances = listOf(heldMicros(1, "BadBoys", 4_995_000)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `a balance that shows below the requirement is still blocked`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(5.0), listOf(badBoys))

        assertEquals(
            GroupAccess.Blocked(requirement),
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(heldMicros(1, "BadBoys", 4_980_000)),
                isStaff = false,
            ),
        )
        assertEquals(
            GroupAccess.Blocked(requirement),
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(heldMicros(1, "BadBoys", 4_994_999)),
                isStaff = false,
            ),
        )
    }

    @Test
    fun `a balance in the wrong token does not count`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))

        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(requirement),
            balances = listOf(held(2, "Other", 1_000.0)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
    }

    @Test
    fun `no named mints accepts any single token`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())),
            balances = listOf(held(2, "Other", 150.0)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `no named mints is measured against everything held, added up`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())

        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(held(1, "BadBoys", 60.0), held(2, "Other", 60.0)),
                isStaff = false,
            ),
        )
        assertEquals(
            GroupAccess.Blocked(requirement),
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(held(1, "BadBoys", 40.0), held(2, "Other", 40.0)),
                isStaff = false,
            ),
        )
    }

    @Test
    fun `a total is rounded once, after adding`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(5.0), emptyList())

        // Each $2.497 shows as $2.50, but together they are $4.994, which shows as $4.99.
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(requirement),
            balances = listOf(heldMicros(1, "BadBoys", 2_497_000), heldMicros(2, "Other", 2_497_000)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
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
                isStaff = false,
            ),
        )
        assertEquals(
            GroupAccess.Eligible,
            GroupAccess.evaluate(
                isMember = false,
                rules = rules(requirement),
                balances = listOf(held(1, "BadBoys", 40.0), held(2, "Other", 120.0)),
                isStaff = false,
            ),
        )
    }

    @Test
    fun `a staff rule blocks a non-staff viewer, with nothing to buy`() {
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.Staff),
            balances = listOf(held(1, "BadBoys", 1_000.0)),
            isStaff = false,
        )

        assertEquals(GroupAccess.Blocked(ChatRuleRequirement.Staff), access)
    }

    @Test
    fun `staff are eligible for a staff chat, holding nothing`() {
        // `UserFlags.is_staff` is the field the rule is written against, so the client can answer
        // it. Treating it as never satisfiable left staff unable to rejoin a chat they had left.
        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(ChatRuleRequirement.Staff),
            balances = emptyList(),
            isStaff = true,
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    @Test
    fun `staff still have to hold the balance a chat asks for`() {
        val requirement = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(badBoys))

        val access = GroupAccess.evaluate(
            isMember = false,
            rules = rules(requirement, ChatRuleRequirement.Staff),
            balances = emptyList(),
            isStaff = true,
        )

        assertEquals(GroupAccess.Blocked(requirement), access)
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
            isStaff = false,
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
            isStaff = false,
        )

        assertEquals(GroupAccess.Eligible, access)
    }

    private fun speaker(vararg speaker: ChatRuleRequirement) =
        ChatRules(listener = emptyList(), speaker = speaker.toList())

    @Test
    fun `no speaker rules means the viewer may speak`() {
        assertEquals(true, canSpeak(rules = null, balances = emptyList(), isStaff = false))
        assertEquals(true, canSpeak(rules = speaker(), balances = emptyList(), isStaff = false))
        // Listener rules are not speaker rules.
        assertEquals(
            true,
            canSpeak(rules(ChatRuleRequirement.Staff), emptyList(), isStaff = false),
        )
    }

    @Test
    fun `a never speaker rule silences everyone, staff included`() {
        val never = speaker(ChatRuleRequirement.Never)
        assertEquals(false, canSpeak(never, listOf(held(1, "BadBoys", 1_000.0)), isStaff = true))
        assertEquals(false, canSpeak(never, emptyList(), isStaff = false))
    }

    @Test
    fun `an unmet speaker balance silences the viewer and a met one does not`() {
        val bar = speaker(ChatRuleRequirement.MinimumBalance(Fiat(500.0), listOf(badBoys)))
        assertEquals(false, canSpeak(bar, listOf(held(1, "BadBoys", 100.0)), isStaff = false))
        assertEquals(true, canSpeak(bar, listOf(held(1, "BadBoys", 600.0)), isStaff = false))
    }

    @Test
    fun `a staff speaker rule silences everyone but staff`() {
        val staff = speaker(ChatRuleRequirement.Staff)
        assertEquals(false, canSpeak(staff, emptyList(), isStaff = false))
        assertEquals(true, canSpeak(staff, emptyList(), isStaff = true))
    }

    @Test
    fun `every speaker rule has to hold`() {
        val both = speaker(
            ChatRuleRequirement.Staff,
            ChatRuleRequirement.MinimumBalance(Fiat(500.0), listOf(badBoys)),
        )
        assertEquals(false, canSpeak(both, listOf(held(1, "BadBoys", 600.0)), isStaff = false))
        assertEquals(false, canSpeak(both, emptyList(), isStaff = true))
        assertEquals(true, canSpeak(both, listOf(held(1, "BadBoys", 600.0)), isStaff = true))
    }

    @Test
    fun `the unmet speaker requirement names a balance before a staff or never rule`() {
        val bar = ChatRuleRequirement.MinimumBalance(Fiat(500.0), listOf(badBoys))
        val rules = speaker(ChatRuleRequirement.Never, ChatRuleRequirement.Staff, bar)
        assertEquals(bar, unmetSpeakerRequirement(rules, emptyList(), isStaff = false))
        assertEquals(
            ChatRuleRequirement.Never,
            unmetSpeakerRequirement(rules, listOf(held(1, "BadBoys", 600.0)), isStaff = true),
        )
    }

    @Test
    fun `a met speaker rule is never named`() {
        val bar = ChatRuleRequirement.MinimumBalance(Fiat(500.0), listOf(badBoys))
        val rules = speaker(bar, ChatRuleRequirement.Staff)
        assertEquals(
            ChatRuleRequirement.Staff,
            unmetSpeakerRequirement(rules, listOf(held(1, "BadBoys", 600.0)), isStaff = false),
        )
        assertEquals(
            null,
            unmetSpeakerRequirement(rules, listOf(held(1, "BadBoys", 600.0)), isStaff = true),
        )
    }
}
