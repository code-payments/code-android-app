package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.GroupBalanceRole
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GroupRulesEditTest {

    private val mint = PublicKey(ByteArray(32) { 4 }.toList())
    private val listenerRule = ChatRuleRequirement.MinimumBalance(Fiat(1000.0, CurrencyCode.JPY), listOf(mint))

    @Test
    fun `only a public group with a listener minimum is editable`() {
        val rules = ChatRules(listener = listOf(listenerRule), speaker = emptyList())

        assertTrue(isBalanceEditable(rules, isPrivate = false))
        assertFalse(isBalanceEditable(rules, isPrivate = true), "private")
        assertFalse(isBalanceEditable(null, isPrivate = false), "no rules")
        assertFalse(
            isBalanceEditable(ChatRules(listener = listOf(ChatRuleRequirement.Staff), speaker = emptyList()), isPrivate = false),
            "no listener minimum",
        )
    }

    @Test
    fun `a rule this build cannot send back rules out the edit`() {
        val rules = ChatRules(listener = listOf(listenerRule), speaker = listOf(ChatRuleRequirement.UnsupportedSpeakerRule))

        assertFalse(isBalanceEditable(rules, isPrivate = false))
    }

    @Test
    fun `a speaker minimum replaces the group's own in place`() {
        val rules = ChatRules(
            listener = listOf(listenerRule),
            speaker = listOf(
                ChatRuleRequirement.Staff,
                ChatRuleRequirement.MinimumBalance(Fiat(2000.0, CurrencyCode.JPY), emptyList()),
                ChatRuleRequirement.Creator,
            ),
        )

        val updated = rules.withMinimum(GroupBalanceRole.Chat, Fiat(3000.0, CurrencyCode.JPY))

        assertEquals(
            listOf(
                ChatRuleRequirement.Staff,
                ChatRuleRequirement.MinimumBalance(Fiat(3000.0, CurrencyCode.JPY), listOf(mint)),
                ChatRuleRequirement.Creator,
            ),
            updated.speaker,
        )
        assertEquals(rules.listener, updated.listener)
    }

    @Test
    fun `removing the speaker minimum leaves every other speaker rule`() {
        val rules = ChatRules(
            listener = listOf(listenerRule),
            speaker = listOf(ChatRuleRequirement.MinimumBalance(Fiat(2000.0, CurrencyCode.JPY), listOf(mint)), ChatRuleRequirement.Creator),
        )

        assertEquals(listOf(ChatRuleRequirement.Creator), rules.withoutSpeakerMinimum().speaker)
        val none = ChatRules(listener = listOf(listenerRule), speaker = emptyList())
        assertSame(none, none.withoutSpeakerMinimum())
    }

    @Test
    fun `an entry is restated in the listener's currency through USD`() {
        val euro = Rate(fx = 2.0, currency = CurrencyCode.EUR)
        val yen = Rate(fx = 150.0, currency = CurrencyCode.JPY)

        assertEquals(Fiat(2250.0, CurrencyCode.JPY), minimumBalanceIn(30.0, euro, CurrencyCode.JPY, yen))
        assertEquals(Fiat(15.0, CurrencyCode.USD), minimumBalanceIn(30.0, euro, CurrencyCode.USD, null))
        assertNull(minimumBalanceIn(30.0, euro, CurrencyCode.JPY, null), "no rate for the listener's currency")
    }

    @Test
    fun `an entry below the currency's minimum transfer value stores nothing`() {
        val usd = Rate(fx = 1.0, currency = CurrencyCode.USD)
        val yen = Rate(fx = 100.0, currency = CurrencyCode.JPY)

        // $0.003 is ¥0.3, under ¥1.
        assertNull(minimumBalanceIn(0.003, usd, CurrencyCode.JPY, yen))
        assertNull(minimumBalanceIn(0.004, usd, CurrencyCode.USD, null))
        assertEquals(Fiat(1.0, CurrencyCode.JPY), minimumBalanceIn(0.01, usd, CurrencyCode.JPY, yen))
    }
}
