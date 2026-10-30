package com.flipcash.libs.currency.math.internal.curves

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flipcash.libs.currency.math.internal.loader.AndroidTableLoader
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * GATE: this repo's discrete bonding curve must reproduce the canonical cross-platform fixtures
 * exactly. The iOS repo asserts the identical fixtures — matching on both sides guarantees the apps
 * price trades identically (a divergence here = one platform computing a different cost/amount than
 * the chain expects). Ground truth = the on-chain Rust curve; both apps load the same u128 tables.
 *
 * Instrumented: the curve loads its binary tables from assets via AndroidTableLoader(Context).
 * Fixture synced from `code/test-vectors/`.
 */
@RunWith(AndroidJUnit4::class)
class DiscreteBondingCurveVectorTest {

    @Test
    fun curve_matches_canonical_vectors() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        DiscreteBondingCurve.initialize(AndroidTableLoader(instrumentation.targetContext)) // loads *.bin
        val curve = DiscreteBondingCurve.getOrThrow()

        val json = instrumentation.context.assets.open("curve.json").bufferedReader().use { it.readText() }
        val vectors = JSONObject(json).getJSONArray("vectors")
        assertTrue("no vectors loaded", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val supply = BigDecimal(v.getInt("currentSupply"))
            val tokens = BigDecimal(v.getInt("tokens"))

            val spot = curve.spotPriceAtSupply(supply).getOrThrow()
            assertEquals("spotPrice mismatch for $name", 0, BigDecimal(v.getString("spotPrice")).compareTo(spot))

            val value = curve.tokensToValue(supply, tokens).getOrThrow()
            assertEquals("tokensToValue mismatch for $name", 0, BigDecimal(v.getString("value")).compareTo(value))
        }
    }

    /** Fractional (sell-path) + rounding-tie cases: fractional supply/tokens via BigDecimal — the
     *  residual divergence risk (iOS rounding-context subtraction vs Android exact subtract). */
    /** Fractional (sell-path) + rounding-tie cases: fractional supply/tokens via BigDecimal — the
     *  residual divergence risk (iOS rounding-context subtraction vs Android exact subtract). */
    @Test
    fun curve_matches_fractional_vectors() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        DiscreteBondingCurve.initialize(AndroidTableLoader(instrumentation.targetContext))
        val curve = DiscreteBondingCurve.getOrThrow()

        val json = instrumentation.context.assets.open("curve_fractional.json").bufferedReader().use { it.readText() }
        val vectors = JSONObject(json).getJSONArray("vectors")
        assertTrue("no vectors loaded", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val supply = BigDecimal(v.getString("currentSupply"))
            val tokens = BigDecimal(v.getString("tokens"))

            val value = curve.tokensToValue(supply, tokens).getOrThrow()
            assertEquals("tokensToValue mismatch for $name", 0, BigDecimal(v.getString("value")).compareTo(value))
        }
    }
}
