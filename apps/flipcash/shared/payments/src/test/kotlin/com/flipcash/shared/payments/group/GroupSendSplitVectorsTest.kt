package com.flipcash.shared.payments.group

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Runs every vector in the shared `test-vectors/group-send-split.json`. */
@RunWith(RobolectricTestRunner::class)
class GroupSendSplitVectorsTest {

    private val fixture: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("group-send-split.json")!!
            .bufferedReader().use { it.readText() },
    )

    @Test
    fun `constants match the fixture`() {
        assertEquals(fixture.getInt("maxRecipients"), GroupSendSplit.MAX_RECIPIENTS)
        assertEquals(cents(fixture.getString("minimumPart")), GroupSendSplit.MINIMUM_PART_UNITS)
    }

    @Test
    fun `every vector`() {
        val vectors = fixture.getJSONArray("vectors")
        assertEquals(23, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val includesSender = v.optBoolean("includesSender", false)
            val plan = GroupSendSplit.split(cents(v.getString("total")), v.getInt("recipients"), includesSender)

            if (v.has("expectedError")) {
                val error = when (val rule = v.getString("expectedError")) {
                    "part-below-minimum" -> GroupSendPlan.PartBelowMinimum
                    "too-many-recipients" -> GroupSendPlan.TooManyRecipients
                    "no-recipients" -> GroupSendPlan.NoRecipients
                    else -> error("$name: unknown rule $rule")
                }
                assertEquals(error, plan, name)
                continue
            }

            val pay = assertIs<GroupSendPlan.Pay>(plan, name)
            val intent = if (pay.parts.size > 1) "SendPublicMultiPayment" else "SendPublicPayment"
            assertEquals(v.getString("intent"), intent, name)

            val parts = v.getJSONArray("parts")
            assertEquals((0 until parts.length()).map { cents(parts.getString(it)) }, pay.parts, name)
            assertEquals(v.optString("kept").ifEmpty { null }?.let(::cents), pay.kept, name)
            if (includesSender) assertEquals(cents(v.getString("sent")), pay.sent, name)

            assertEquals(v.optString("eachLabel").ifEmpty { null }, pay.takeIf { it.shares.size > 1 }?.let(::eachLabel), name)
        }
    }

    // Not in the fixture: the paid parts are equal but the sender's is a cent less, so "~" must
    // come from the sender's share. 5 cents between You and 2 pays 2 and 2 and keeps 1.
    @Test
    fun `the sender's smaller share makes the label approximate`() {
        val pay = assertIs<GroupSendPlan.Pay>(GroupSendSplit.split(5, 2, includesSender = true))
        assertEquals(listOf(2L, 2L), pay.parts)
        assertEquals(1L, pay.kept)
        assertEquals("~$0.01 Each", eachLabel(pay))
    }

    private fun eachLabel(pay: GroupSendPlan.Pay): String {
        val each = "$" + BigDecimal.valueOf(pay.smallest, 2).toPlainString() + " Each"
        return if (pay.isEven) each else "~$each"
    }

    @Test
    fun `minor units round trip through Fiat`() {
        assertEquals(12345L, GroupSendSplit.minorUnits(Fiat("123.45", CurrencyCode.USD)))
        assertEquals(1L, GroupSendSplit.minorUnits(Fiat("0.01", CurrencyCode.USD)))
        assertEquals(500L, GroupSendSplit.minorUnits(Fiat("500", CurrencyCode.JPY)))
        assertEquals(Fiat("0.34", CurrencyCode.USD), GroupSendSplit.fiat(34, CurrencyCode.USD))
        assertEquals(Fiat("7", CurrencyCode.JPY), GroupSendSplit.fiat(7, CurrencyCode.JPY))
    }

    private fun cents(usd: String): Long = BigDecimal(usd).movePointRight(2).longValueExact()
}
