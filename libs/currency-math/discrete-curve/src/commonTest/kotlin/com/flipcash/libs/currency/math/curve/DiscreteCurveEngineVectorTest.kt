package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: the shared discrete bonding curve engine must reproduce the canonical cross-platform
 * fixtures exactly. Ground truth = the on-chain Rust curve; both apps load the same u128 tables.
 * Fixtures synced from `code/test-vectors/`.
 *
 * The two `.bin` tables are compiled in as Base64-encoded text fixtures (`*.bin.b64`) rather than
 * raw bytes -- `flipcash.kmp.test.fixtures`'s generated `readTestResource(name)` only returns
 * `String` (it compiles fixtures via `file.readText()`), and Kotlin/Native test binaries ship no
 * resource bundle for a real binary-file loader to read from anyway.
 */
class DiscreteCurveEngineVectorTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun readTestResourceBytes(name: String): ByteArray =
        Base64.decode(readTestResource("$name.bin.b64").trim())

    @BeforeTest
    fun setUp() {
        if (!DiscreteCurveTables.isInitialized) {
            DiscreteCurveEngine.initialize(
                pricingTableBytes = readTestResourceBytes("discrete_pricing_table"),
                cumulativeTableBytes = readTestResourceBytes("discrete_cumulative_table"),
            )
        }
    }

    @Test
    fun curve_matches_canonical_vectors() {
        val json = readTestResource("curve.json")
        val vectors = Json.parseToJsonElement(json).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val supply = BigDecimal.fromInt(v["currentSupply"]!!.jsonPrimitive.content.toInt())
            val tokens = BigDecimal.fromInt(v["tokens"]!!.jsonPrimitive.content.toInt())

            val spot = DiscreteCurveEngine.spotPriceAtSupply(v["currentSupply"]!!.jsonPrimitive.content.toInt(), maxSupply = 21_000_000)
            assertEquals(0, BigDecimal.parseString(v["spotPrice"]!!.jsonPrimitive.content).compareTo(spot!!), "spotPrice mismatch for $name")

            val value = DiscreteCurveEngine.tokensToValue(supply, tokens)
            assertEquals(0, BigDecimal.parseString(v["value"]!!.jsonPrimitive.content).compareTo(value!!), "tokensToValue mismatch for $name")
        }
    }

    @Test
    fun curve_matches_fractional_vectors() {
        val json = readTestResource("curve_fractional.json")
        val vectors = Json.parseToJsonElement(json).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val supply = BigDecimal.parseString(v["currentSupply"]!!.jsonPrimitive.content)
            val tokens = BigDecimal.parseString(v["tokens"]!!.jsonPrimitive.content)

            val value = DiscreteCurveEngine.tokensToValue(supply, tokens)
            assertEquals(0, BigDecimal.parseString(v["value"]!!.jsonPrimitive.content).compareTo(value!!), "tokensToValue mismatch for $name")
        }
    }
}
