package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The info card's rules line. Only a minimum balance has anything to say on it — staff membership
 * is not something a user can go and acquire, so showing it as a requirement would be an
 * instruction with no action behind it.
 */
class ChatRulesSummaryTest {

    private val mint = PublicKey(ByteArray(32) { 5 }.toList())

    @Test
    fun `a minimum balance names the amount and the ticker`() {
        val rules = ChatRules(
            listener = listOf(ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(mint))),
            speaker = emptyList(),
        )

        assertEquals(
            ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(mint)),
            rules.balanceRequirement(),
        )
    }

    @Test
    fun `a speaker balance rule counts when there is no listener rule`() {
        val rules = ChatRules(
            listener = emptyList(),
            speaker = listOf(ChatRuleRequirement.MinimumBalance(Fiat(50.0), emptyList())),
        )

        assertEquals(Fiat(50.0), (rules.balanceRequirement())?.amount)
    }

    @Test
    fun `a listener rule wins over a speaker rule`() {
        val rules = ChatRules(
            listener = listOf(ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())),
            speaker = listOf(ChatRuleRequirement.MinimumBalance(Fiat(500.0), emptyList())),
        )

        assertEquals(Fiat(100.0), rules.balanceRequirement()?.amount)
    }

    @Test
    fun `staff-only rules have no line to render`() {
        val rules = ChatRules(listener = listOf(ChatRuleRequirement.Staff), speaker = emptyList())

        assertNull(rules.balanceRequirement())
    }

    @Test
    fun `no rules at all have no line to render`() {
        assertNull(ChatRules(emptyList(), emptyList()).balanceRequirement())
        assertNull((null as ChatRules?).balanceRequirement())
    }
}
