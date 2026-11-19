package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.util.resources.AndroidResources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `collection_funding.json`, a verbatim copy of the orchestrator's canonical
 * `test-vectors/collection_funding.json`. A failure here is either a real regression or a
 * cross-platform decision that has to be made in the canonical fixture and re-synced, never a local
 * edit.
 *
 * The fixture's strings say "Apple Pay"; Android pays a top-up with Google Pay, so the expected
 * sublabels are read with that one name swapped.
 *
 * Every mismatch across a run is collected, so one failure lists every case that disagrees.
 *
 * The formatter reads the app's real string resources, so the vectors' English holds for what
 * Android renders.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionFundingVectorsTest {

    private val fixture: JsonObject = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/collection_funding.json")!!.bufferedReader().use { it.readText() },
    ).jsonObject

    private val minorDigits = fixture.obj("constants").int("minor_digits")
    private val formatter = CollectionFundingFormatter(
        currency = CurrencyCode.USD,
        minorDigits = minorDigits,
        topUpSourceName = "Google Pay",
        strings = ResourceCollectionFundingStrings(AndroidResources(RuntimeEnvironment.getApplication())),
    )

    @Test
    fun `the dust floor is half the minor unit`() {
        val unit = minorUnitMicros(minorDigits)
        val floor = fixture.obj("constants").long("dust_floor_micros")
        assertEquals(unit / 2, floor)
        assertEquals(true, isDust(floor - 1, unit))
        assertEquals(false, isDust(floor, unit))
    }

    @Test
    fun `every case plans and draws as the fixture says`() {
        val cases = fixture.array("cases")
        assertTrue(cases.isNotEmpty(), "collection_funding.json loaded no cases")
        val names = cases.map { it.jsonObject.string("name") }
        val required = listOf(
            "half-cent-tie-leg", "half-cent-tie-combined", "half-micro-fee-drain",
            "non-cent-balance-drain", "non-cent-balance-partial", "pool-fee-250-bps",
        )
        assertTrue(names.containsAll(required), "missing ${required - names.toSet()}")

        val failures = mutableListOf<String>()
        var ran = 0
        for (element in cases) {
            val case = element.jsonObject
            val name = case.string("name")
            val input = case.obj("input")
            val expected = case.obj("expected")

            val balances = input.array("balances").map { it.jsonObject }
            for (balance in balances.filter { it.string("kind") == "dollars" }) {
                failures.check(name, "Dollars id", DOLLARS, balance.string("id"))
                failures.check(name, "Dollars fee", fixture.obj("constants").int("dollars_fee_bps"), balance.int("sell_fee_bps"))
            }
            failures.check(name, "currency", "USD", input.string("currency"))
            failures.check(name, "minor digits", minorDigits, input.int("minor_digits"))

            val plan = planCollectionFunding(
                amountMicros = input.long("amount_micros"),
                minorDigits = input.int("minor_digits"),
                minTopUpMicros = input.long("min_top_up_micros"),
                dollarsId = DOLLARS,
                selected = input.stringOrNull("selected"),
                lastPaid = input.stringOrNull("last_paid"),
                balances = balances.map {
                    FundingBalance(it.string("id"), it.long("fiat_micros"), it.int("sell_fee_bps"))
                },
            )

            failures.check(name, "paying currency", expected.string("selected"), plan.payingCurrency)
            failures.check(name, "payable without top-up", expected.boolean("payable_without_top_up"), plan.payableWithoutTopUp)
            failures.check(name, "shortfall", expected.long("shortfall_micros"), plan.shortfallMicros)
            failures.check(name, "top-up", expected.long("top_up_micros"), plan.topUpMicros)
            failures.check(name, "total fee", expected.long("total_fee_micros"), plan.totalFeeMicros)
            failures.check(name, "legs", expected.array("legs").map { it.jsonObject.toLeg(name, failures) }, plan.legs)

            val currencyNames = balances.associate { it.string("id") to it.string("name") } + (DOLLARS to "Dollars")
            val display = formatter.display(plan, input.long("amount_micros")) { currencyNames[it] ?: it }
            val want = case.obj("display")
            failures.check(name, "short_by", want.stringOrNull("short_by"), display.shortBy)
            failures.check(name, "add_button", want.stringOrNull("add_button"), display.addButton)
            failures.check(name, "top_up", want.stringOrNull("top_up"), display.topUp)
            failures.check(name, "modal_fee", want.stringOrNull("modal_fee"), display.modalFee)
            val details = want.obj("details")
            failures.check(name, "details.pay", details.obj("pay").toRow(), display.details.pay)
            failures.check(name, "details.use", details.array("use").map { it.jsonObject.toRow() }, display.details.use)
            val total = details.obj("total")
            failures.check(name, "details.total", PaymentDetailsRow(total.string("label"), total.string("amount")), display.details.total)
            ran++
        }

        println("collection_funding: $ran of ${cases.size} cases ran")
        assertEquals(cases.size, ran)
        failures.assertNone()
    }

    @Test
    fun `every fee display row has its kind and strings`() {
        val rows = fixture.array("fee_display")
        assertTrue(rows.isNotEmpty(), "collection_funding.json loaded no fee_display rows")

        val failures = mutableListOf<String>()
        for (element in rows) {
            val row = element.jsonObject
            val fee = row.long("fee_micros")
            val conversion = row.boolean("conversion")
            val label = "fee $fee, conversion $conversion"
            val kind = conversionFeeKind(fee, conversion, minorDigits)

            failures.check(label, "kind", row.string("kind"), kind.wireName)
            val rounded = if (kind == ConversionFeeKind.Exact || kind == ConversionFeeKind.Approx) {
                roundedMinorUnits(fee, minorDigits)
            } else {
                null
            }
            failures.check(label, "rounded_minor", row["rounded_minor"]?.jsonPrimitive?.contentOrNull?.toLong(), rounded)
            failures.check(label, "modal", row.stringOrNull("modal"), formatter.modalFee(fee, conversion))
            failures.check(label, "details_sublabel", row.stringOrNull("details_sublabel"), formatter.feeSublabel(fee, conversion))
        }

        println("collection_funding: ${rows.size} fee_display rows ran")
        failures.assertNone()
    }

    @Test
    fun `every amount display row rounds half-up to the cent`() {
        val rows = fixture.array("amount_display")
        assertTrue(rows.isNotEmpty(), "collection_funding.json loaded no amount_display rows")

        val failures = mutableListOf<String>()
        for (element in rows) {
            val row = element.jsonObject
            val micros = row.long("micros")
            failures.check("amount $micros", "text", row.string("text"), formatter.amount(micros))
        }

        println("collection_funding: ${rows.size} amount_display rows ran")
        failures.assertNone()
    }

    private fun JsonObject.toLeg(case: String, failures: MutableList<String>): FundingLeg {
        val hasBalance = containsKey("from_balance_micros")
        val hasTopUp = containsKey("from_top_up_micros")
        if (hasBalance != hasTopUp) failures += "`$case`: a fixture leg carries half a Dollars split"
        return FundingLeg(
            source = string("source"),
            conversion = boolean("conversion"),
            soldMicros = long("sold_micros"),
            feeMicros = long("fee_micros"),
            deliveredMicros = long("delivered_micros"),
            dollarsSplit = if (hasBalance && hasTopUp) {
                DollarsSplit(long("from_balance_micros"), long("from_top_up_micros"))
            } else {
                null
            },
        )
    }

    /** The fixture's "You’ll Pay" rows carry no sublabels key, which reads as no sublabels. */
    private fun JsonObject.toRow() = PaymentDetailsRow(
        name = string("name"),
        amount = string("amount"),
        sublabels = (get("sublabels") as JsonArray?).orEmpty().map {
            it.jsonPrimitive.content.replace(" from Apple Pay", " from Google Pay")
        },
    )

    private val ConversionFeeKind.wireName: String
        get() = when (this) {
            ConversionFeeKind.None -> "none"
            ConversionFeeKind.Zero -> "zero"
            ConversionFeeKind.UnderMinor -> "under_minor"
            ConversionFeeKind.Exact -> "exact"
            ConversionFeeKind.Approx -> "approx"
        }

    private fun MutableList<String>.check(case: String, what: String, expected: Any?, actual: Any?) {
        if (expected != actual) add("`$case` $what:\n    expected $expected\n    actual   $actual")
    }

    private fun List<String>.assertNone() {
        if (isNotEmpty()) fail("$size mismatches:\n" + joinToString("\n"))
    }

    private companion object {
        const val DOLLARS = "dollars"

        fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
        fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray
        fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
        fun JsonObject.stringOrNull(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
        fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
        fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
        fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean
    }
}
