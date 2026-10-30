package com.flipcash.services.internal.network.extensions

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.IdempotencyKey
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel

/**
 * The balance gate, from the form's draft to the `StartChat` request and back.
 *
 * What the group creation flow sets is one listener [ChatRuleRequirement.MinimumBalance] and nothing
 * else, so these cover that shape specifically: the speaker list stays empty, the amount keeps its
 * currency, and the single-mint list survives. The round trip matters because the requirement the
 * creator typed and the requirement the join gate later evaluates come from opposite directions —
 * the first through `asProtoRules`, the second through `toChatRules` off `GetChat`.
 */
class ChatRulesProtoMappingTest {

    @Test
    fun `a listener minimum balance maps to the listener list and leaves speaker empty`() {
        val proto = ChatRules(
            listener = listOf(ChatRuleRequirement.MinimumBalance(Fiat(100), listOf(mint(7)))),
            speaker = emptyList(),
        ).asProtoRules()

        assertEquals(1, proto.listenerCount)
        assertEquals(0, proto.speakerCount)

        val requirement = proto.getListener(0)
        assertEquals(ChatModel.ListenerRules.KindCase.MINIMUM_BALANCE, requirement.kindCase)
        assertEquals(100.0, requirement.minimumBalance.amount.nativeAmount)
        assertEquals("usd", requirement.minimumBalance.amount.currency)
        assertEquals(1, requirement.minimumBalance.mintsCount)
        assertEquals(
            mint(7).bytes,
            requirement.minimumBalance.getMints(0).value.toByteArray().toList(),
        )
    }

    @Test
    fun `an empty mint list stays empty, meaning any mint`() {
        val proto = ChatRules(
            listener = listOf(ChatRuleRequirement.MinimumBalance(Fiat(50), emptyList())),
            speaker = emptyList(),
        ).asProtoRules()

        assertEquals(0, proto.getListener(0).minimumBalance.mintsCount)
    }

    @Test
    fun `the requirement the creator set is the requirement the gate reads back`() {
        val rules = ChatRules(
            listener = listOf(
                ChatRuleRequirement.MinimumBalance(
                    amount = Fiat(100, CurrencyCode.USD),
                    mints = listOf(mint(3)),
                )
            ),
            speaker = emptyList(),
        )

        assertEquals(rules, rules.asProtoRules().toChatRules())
    }

    @Test
    fun `a staff requirement maps without an amount`() {
        val proto = ChatRules(
            listener = listOf(ChatRuleRequirement.Staff),
            speaker = emptyList(),
        ).asProtoRules()

        assertEquals(ChatModel.ListenerRules.KindCase.STAFF, proto.getListener(0).kindCase)
        assertTrue(proto.getListener(0).hasStaff())
    }

    @Test
    fun `an idempotency key crosses the wire as its 16 bytes`() {
        // Spelled out rather than read from the model: the size is the contract's, and a test that
        // takes it from the same constant it is checking would pass on a wrong one.
        val key = IdempotencyKey(ByteArray(16) { it.toByte() })

        val proto = key.asProtoIdempotencyKey()

        assertEquals(16, proto.value.size())
        assertEquals(key.bytes.toList(), proto.value.toByteArray().toList())
    }

    private fun mint(seed: Byte) = Mint(ByteArray(32) { seed }.toList())
}
