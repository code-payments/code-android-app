package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.SampledChatter
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of iOS's `GroupProfileStateTests`. */
class GroupProfileStateTest {

    private fun mint(seed: Byte) = Mint(ByteArray(32) { seed }.toList())

    private fun token(seed: Byte) = MintMetadata(
        address = mint(seed),
        decimals = 6,
        name = "T$seed",
        symbol = "T$seed",
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

    private fun held(seed: Byte, dollars: Double) =
        TokenWithBalance(token = token(seed), balance = Fiat(dollars))

    private fun minimum(amount: Fiat, mints: List<PublicKey> = emptyList()) =
        ChatRuleRequirement.MinimumBalance(amount, mints)

    private fun minimum(dollars: Double, mints: List<PublicKey> = emptyList()) =
        minimum(Fiat(dollars), mints)

    private fun rules(
        listener: List<ChatRuleRequirement> = emptyList(),
        speaker: List<ChatRuleRequirement> = emptyList(),
    ) = ChatRules(listener = listener, speaker = speaker)

    private fun standing(
        isMember: Boolean,
        rules: ChatRules?,
        balances: List<TokenWithBalance> = emptyList(),
        rates: Map<CurrencyCode, Rate> = emptyMap(),
    ) = GroupProfileStanding.of(
        isMember = isMember,
        rules = rules,
        balances = balances,
        isStaff = false,
        rates = rates,
        viewerId = null,
        creatorId = null,
    )

    // CTA

    @Test
    fun `non-member short of the join minimum buys to join`() {
        val join = minimum(10.0)
        assertEquals(
            GroupProfileCta.BuyToJoin(join),
            standing(false, rules(listener = listOf(join))).cta,
        )
    }

    @Test
    fun `non-member who meets the join minimum joins`() {
        assertEquals(
            GroupProfileCta.Join,
            standing(false, rules(listener = listOf(minimum(10.0))), listOf(held(1, 20.0))).cta,
        )
    }

    @Test
    fun `non-member of a group with no rules joins`() {
        assertEquals(GroupProfileCta.Join, standing(false, null).cta)
        assertEquals(GroupProfileCta.Join, standing(false, rules()).cta)
    }

    @Test
    fun `non-member of a staff-only group has nothing to do`() {
        assertEquals(
            GroupProfileCta.None,
            standing(false, rules(listener = listOf(ChatRuleRequirement.Staff))).cta,
        )
    }

    @Test
    fun `non-member with a minimum in a currency with no rate has nothing to do yet`() {
        assertEquals(
            GroupProfileCta.None,
            standing(false, rules(listener = listOf(minimum(Fiat(10.0, CurrencyCode.CAD))))).cta,
        )
    }

    @Test
    fun `member who may speak opens the chat`() {
        assertEquals(
            GroupProfileCta.OpenChat,
            standing(
                true,
                rules(listener = listOf(minimum(10.0)), speaker = listOf(minimum(100.0))),
                listOf(held(1, 500.0)),
            ).cta,
        )
    }

    @Test
    fun `member short of the chat minimum only buys to chat`() {
        val chat = minimum(100.0)
        assertEquals(
            GroupProfileCta.BuyToChat(chat),
            standing(
                true,
                rules(listener = listOf(minimum(10.0)), speaker = listOf(chat)),
                listOf(held(1, 12.0)),
            ).cta,
        )
    }

    @Test
    fun `member short of both minimums buys to chat for the larger one`() {
        val chat = minimum(100.0)
        assertEquals(
            GroupProfileCta.BuyToChat(chat),
            standing(true, rules(listener = listOf(minimum(10.0)), speaker = listOf(chat))).cta,
        )
    }

    @Test
    fun `member under the join minimum alone buys to chat`() {
        val join = minimum(10.0)
        assertEquals(
            GroupProfileCta.BuyToChat(join),
            standing(true, rules(listener = listOf(join))).cta,
        )
    }

    @Test
    fun `member of a read-only group opens the chat`() {
        assertEquals(
            GroupProfileCta.OpenChat,
            standing(true, rules(speaker = listOf(ChatRuleRequirement.Never))).cta,
        )
    }

    // Requirements

    @Test
    fun `requirements carry a join and a chat minimum`() {
        val join = minimum(10.0)
        val chat = minimum(100.0)
        assertEquals(
            GroupBalanceRequirements(join, chat),
            GroupBalanceRequirements.from(rules(listener = listOf(join), speaker = listOf(chat))),
        )
    }

    @Test
    fun `chat falls back to the join minimum`() {
        val join = minimum(10.0)
        assertEquals(
            GroupBalanceRequirements(join, join),
            GroupBalanceRequirements.from(
                rules(listener = listOf(join), speaker = listOf(ChatRuleRequirement.Staff))
            ),
        )
    }

    @Test
    fun `a chat-only minimum leaves join empty`() {
        val chat = minimum(100.0)
        assertEquals(
            GroupBalanceRequirements(null, chat),
            GroupBalanceRequirements.from(rules(speaker = listOf(chat))),
        )
    }

    @Test
    fun `requirements are hidden without a minimum`() {
        assertNull(GroupBalanceRequirements.from(null))
        assertNull(GroupBalanceRequirements.from(rules()))
        assertNull(
            GroupBalanceRequirements.from(
                rules(
                    listener = listOf(ChatRuleRequirement.Staff),
                    speaker = listOf(ChatRuleRequirement.Never),
                )
            )
        )
    }

    // Sole token

    @Test
    fun `a token named by both requirements is the sole token`() {
        val rules = rules(
            listener = listOf(minimum(10.0, listOf(mint(1)))),
            speaker = listOf(minimum(25.0, listOf(mint(1)))),
        )
        assertEquals(mint(1), GroupBalanceRequirements.from(rules)?.soleToken)
    }

    @Test
    fun `a token beside an any-holding requirement is the sole token`() {
        val rules = rules(
            listener = listOf(minimum(10.0)),
            speaker = listOf(minimum(25.0, listOf(mint(1)))),
        )
        assertEquals(mint(1), GroupBalanceRequirements.from(rules)?.soleToken)
    }

    @Test
    fun `two different tokens leave no sole token`() {
        val rules = rules(
            listener = listOf(minimum(10.0, listOf(mint(1)))),
            speaker = listOf(minimum(25.0, listOf(mint(2)))),
        )
        assertNull(GroupBalanceRequirements.from(rules)?.soleToken)
    }

    @Test
    fun `the reserve alone is not a sole token`() {
        val rules = rules(listener = listOf(minimum(10.0, listOf(Mint.usdf))))
        assertNull(GroupBalanceRequirements.from(rules)?.soleToken)
    }

    @Test
    fun `requirements naming no token have no sole token`() {
        val rules = rules(listener = listOf(minimum(10.0)), speaker = listOf(minimum(25.0)))
        assertNull(assertNotNull(GroupBalanceRequirements.from(rules)).soleToken)
    }

    // Shortfall

    @Test
    fun `shortfall counts every holding when no mint is named`() {
        assertEquals(
            Fiat(3.67),
            balanceShortfall(minimum(10.0), listOf(held(1, 3.0), held(2, 3.33)), emptyMap()),
        )
    }

    @Test
    fun `shortfall counts only the named mint`() {
        assertEquals(
            Fiat(6.0),
            balanceShortfall(
                minimum(10.0, listOf(mint(1))),
                listOf(held(1, 4.0), held(2, 100.0)),
                emptyMap(),
            ),
        )
    }

    @Test
    fun `shortfall is restated in the requirement's currency and rounded up`() {
        // 10 CAD at 1.25 per dollar is $8; holding $2.40 leaves $5.60, which is 7 CAD.
        val rates = mapOf(CurrencyCode.CAD to Rate(1.25, CurrencyCode.CAD))
        assertEquals(
            Fiat(7.0, CurrencyCode.CAD),
            balanceShortfall(minimum(Fiat(10.0, CurrencyCode.CAD)), listOf(held(1, 2.4)), rates),
        )
    }

    @Test
    fun `shortfall is null once the requirement is met`() {
        assertNull(balanceShortfall(minimum(10.0), listOf(held(1, 10.0)), emptyMap()))
    }

    // Gate funding

    @Test
    fun `funding buys when one other balance covers the shortfall`() {
        // $6 short of $10 of mint 1; mint 2 alone holds $6.
        assertEquals(
            GateFunding.Buy,
            gateFunding(minimum(10.0, listOf(mint(1))), listOf(held(1, 4.0), held(2, 6.0)), emptyMap()),
        )
    }

    @Test
    fun `funding adds money when no single balance covers the shortfall`() {
        // $6 short; $3 + $3 would cover it, but the buy pays from one source.
        assertEquals(
            GateFunding.AddMoney,
            gateFunding(
                minimum(10.0, listOf(mint(1))),
                listOf(held(1, 4.0), held(2, 3.0), held(3, 3.0)),
                emptyMap(),
            ),
        )
    }

    @Test
    fun `funding does not pay with the gated token itself`() {
        assertEquals(
            GateFunding.AddMoney,
            gateFunding(minimum(10.0, listOf(mint(1))), listOf(held(1, 4.0)), emptyMap()),
        )
    }

    @Test
    fun `funding without a rate falls back to any spendable source`() {
        val rule = minimum(Fiat(10.0, CurrencyCode.CAD), listOf(mint(1)))
        assertEquals(GateFunding.Buy, gateFunding(rule, listOf(held(2, 0.5)), emptyMap()))
        assertEquals(GateFunding.AddMoney, gateFunding(rule, listOf(held(1, 4.0)), emptyMap()))
    }

    // Chatting grid

    private fun chatter() = SampledChatter(
        userProfile = UserProfile(
            displayName = "Ted",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
        ),
        lastSentAt = null,
        isCreator = false,
    )

    @Test
    fun `a private group hides the grid even with chatters`() {
        assertFalse(isChattingGridVisible(isPrivate = true, chatters = listOf(chatter())))
    }

    @Test
    fun `no chatters hides the grid`() {
        assertFalse(isChattingGridVisible(isPrivate = false, chatters = emptyList()))
    }

    @Test
    fun `a public group with chatters shows the grid`() {
        assertTrue(isChattingGridVisible(isPrivate = false, chatters = listOf(chatter())))
    }

    // Your Balance

    @Test
    fun `your balance reads the join minimum's mint`() {
        val requirements = GroupBalanceRequirements(
            join = minimum(10.0, listOf(mint(1))),
            chat = minimum(100.0, listOf(mint(2))),
        )
        assertEquals(
            Fiat(4.0),
            yourBalance(requirements, listOf(held(1, 4.0), held(2, 50.0)), emptyMap()),
        )
    }

    @Test
    fun `your balance is hidden when its currency has no rate`() {
        val requirements = GroupBalanceRequirements(minimum(Fiat(10.0, CurrencyCode.CAD)), null)
        assertNull(yourBalance(requirements, listOf(held(1, 4.0)), emptyMap()))
    }
}
