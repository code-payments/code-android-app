package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: `curve.json` and `curve_fractional.json` through currency-math's [BondingCurve], on every
 * target. On Android and the JVM the decimal is `java.math.BigDecimal`; on Node it is
 * CommonBigDecimal, so this is where a rounding difference in the port would show.
 *
 * Results are compared as plain strings, not with `compareTo`, so a value that is numerically
 * equal but carries a different scale also fails.
 */
class BondingCurveVectorTest {

    private suspend fun curve(): BondingCurve {
        Curves.initializeFromSourceTree()
        return DiscreteBondingCurve.getOrThrow()
    }

    private fun vectors(name: String): List<JsonObject> =
        Json.parseToJsonElement(readTestResource(name)).jsonObject.getValue("vectors").jsonArray
            .map { it.jsonObject }
            .also { assertTrue(it.isNotEmpty(), "no vectors in $name") }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun curveVectors() = runTest {
        val curve = curve()
        for (v in vectors("curve.json")) {
            val name = v.str("name")
            val supply = BigDecimal(v.str("currentSupply"))
            val tokens = BigDecimal(v.str("tokens"))

            val spot = curve.spotPriceAtSupply(supply).getOrThrow()
            assertEquals(0, BigDecimal(v.str("spotPrice")).compareTo(spot), "spotPrice for $name: $spot")

            val value = curve.tokensToValue(supply, tokens).getOrThrow()
            assertEquals(0, BigDecimal(v.str("value")).compareTo(value), "value for $name: $value")
            // Exact: the 18-dp on-chain integer.
            assertEquals(v.str("valueScaled"), value.movePointLeft(-18).toPlainString().substringBefore('.'), "valueScaled for $name")
            println("curve.json $name spot=${spot.toPlainString()} value=${value.toPlainString()}")
        }
    }

    @Test
    fun fractionalVectors() = runTest {
        val curve = curve()
        for (v in vectors("curve_fractional.json")) {
            val name = v.str("name")
            val value = curve.tokensToValue(BigDecimal(v.str("currentSupply")), BigDecimal(v.str("tokens"))).getOrThrow()
            assertEquals(0, BigDecimal(v.str("value")).compareTo(value), "value for $name: $value")
            println("curve_fractional.json $name value=${value.toPlainString()}")
        }
    }
}
