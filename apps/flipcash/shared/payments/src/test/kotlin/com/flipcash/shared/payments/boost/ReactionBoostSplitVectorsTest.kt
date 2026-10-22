package com.flipcash.shared.payments.boost

import com.getcode.opencode.model.core.ID
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Runs every vector in the shared `test-vectors/reaction-boost-split.json`. */
@RunWith(RobolectricTestRunner::class)
class ReactionBoostSplitVectorsTest {

    private val creator: ID = List(32) { 1 }
    private val member: ID = List(32) { 2 }
    private val payingMember: ID = List(32) { 3 }
    private val group = BoostChat.Group(creator)

    private val fixture: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("reaction-boost-split.json")!!
            .bufferedReader().use { it.readText() },
    )

    @Test
    fun `constants match the fixture`() {
        assertEquals(fixture.getLong("creatorPercent"), ReactionBoostSplit.CREATOR_PERCENT)
        assertEquals(cents(fixture.getString("minimumShare")), ReactionBoostSplit.MINIMUM_SHARE_CENTS)
        val minimumTwoPayee = cents(fixture.getString("minimumTwoPayeeTotal"))
        assertIs<ReactionBoostPlan.Pay>(ReactionBoostSplit.plan(payingMember, member, group, minimumTwoPayee))
        assertEquals(
            ReactionBoostPlan.ShareBelowMinimum,
            ReactionBoostSplit.plan(payingMember, member, group, minimumTwoPayee - 1),
        )
    }

    @Test
    fun `every vector`() {
        val vectors = fixture.getJSONArray("vectors")
        assertEquals(24, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val dm = when (val kind = v.getString("chat")) {
                "group" -> false
                "dm" -> true
                else -> error("$name: unknown chat $kind")
            }
            val chat = if (dm) BoostChat.Dm else group
            val sender = when (v.getString("messageSender")) {
                "creator" -> creator
                "member" -> member
                else -> error("$name: unknown messageSender")
            }
            val payer = when (v.getString("payer")) {
                "creator" -> creator
                "member" -> payingMember
                "sender" -> sender
                else -> error("$name: unknown payer")
            }
            val plan = ReactionBoostSplit.plan(payer, sender, chat, cents(v.getString("total")))
            val expected = v.getJSONObject("expected")

            if (expected.has("expectedError")) {
                val error = when (val rule = expected.getString("expectedError")) {
                    "self-boost" -> ReactionBoostPlan.SelfBoost
                    "boost share below minimum transfer value",
                    "boost amount below minimum transfer value",
                    -> ReactionBoostPlan.ShareBelowMinimum
                    else -> error("$name: unknown rule $rule")
                }
                assertEquals(error, plan, name)
                continue
            }

            val pay = assertIs<ReactionBoostPlan.Pay>(plan, name)
            val intent = if (pay.shares.size > 1) "SendPublicMultiPayment" else "SendPublicPayment"
            assertEquals(expected.getString("intent"), intent, name)

            val payees = expected.getJSONArray("payees")
            val expectedShares = (0 until payees.length()).map { j ->
                val p = payees.getJSONObject(j)
                val role = when (p.getString("role")) {
                    "sender" -> BoostPayee.SENDER
                    "creator" -> BoostPayee.CREATOR
                    else -> error("$name: unknown role")
                }
                BoostShare(
                    payee = if (role == BoostPayee.SENDER) sender else creator,
                    role = role,
                    cents = cents(p.getString("nativeAmount")),
                )
            }
            assertEquals(expectedShares, pay.shares, name)
        }
    }

    private fun cents(usd: String): Long = BigDecimal(usd).movePointRight(2).longValueExact()
}
