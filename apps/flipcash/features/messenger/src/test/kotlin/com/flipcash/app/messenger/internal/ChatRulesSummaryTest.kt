package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The info card's rules lines. A minimum balance carries an amount to state; staff membership is a
 * yes or no, answered separately, because a chat can set both.
 */
class ChatRulesSummaryTest {

    private val mint = PublicKey(ByteArray(32) { 5 }.toList())

    @Test
    fun `a minimum balance names the amount and the currency`() {
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
    fun `a staff-only rule has no balance to state, but is still stated`() {
        val rules = ChatRules(listener = listOf(ChatRuleRequirement.Staff), speaker = emptyList())

        assertNull(rules.balanceRequirement())
        assertTrue(rules.requiresStaff())
    }

    @Test
    fun `a speaker staff rule counts too`() {
        val rules = ChatRules(listener = emptyList(), speaker = listOf(ChatRuleRequirement.Staff))

        assertTrue(rules.requiresStaff())
    }

    @Test
    fun `a chat can ask for both, and both are stated`() {
        val balance = ChatRuleRequirement.MinimumBalance(Fiat(100.0), listOf(mint))
        val rules = ChatRules(
            listener = listOf(balance, ChatRuleRequirement.Staff),
            speaker = emptyList(),
        )

        assertEquals(balance, rules.balanceRequirement())
        assertTrue(rules.requiresStaff())
    }

    @Test
    fun `no rules at all have no line to render`() {
        assertNull(ChatRules(emptyList(), emptyList()).balanceRequirement())
        assertNull((null as ChatRules?).balanceRequirement())
        assertFalse(ChatRules(emptyList(), emptyList()).requiresStaff())
        assertFalse((null as ChatRules?).requiresStaff())
    }
}
