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
        assertEquals(14, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val plan = GroupSendSplit.split(cents(v.getString("total")), v.getInt("recipients"))

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

            if (v.has("eachLabel")) {
                val each = "$" + BigDecimal.valueOf(pay.smallest, 2).toPlainString() + " Each"
                assertEquals(v.getString("eachLabel"), if (pay.isEven) each else "~$each", name)
            }
        }
    }

    @Test
    fun `every You vector`() {
        val vectors = fixture.getJSONArray("youVectors")
        assertEquals(11, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val total = cents(v.getString("total"))
            val plan = GroupSendSplit.splitWithViewer(total, v.getInt("recipients"), v.getInt("youAt"))

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
            assertEquals(cents(v.getString("paidTotal")), pay.parts.sum(), name)
            assertEquals(cents(v.getString("youPart")), total - pay.parts.sum(), name)

            if (v.has("eachLabel")) {
                val each = "$" + BigDecimal.valueOf(pay.smallest, 2).toPlainString() + " Each"
                assertEquals(v.getString("eachLabel"), if (pay.isEven) each else "~$each", name)
            }
        }
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
