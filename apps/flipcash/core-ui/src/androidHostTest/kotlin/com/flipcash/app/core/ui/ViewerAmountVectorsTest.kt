package com.flipcash.app.core.ui

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `test-vectors/viewer_amount.json`, a verbatim copy of the orchestrator's canonical fixture that
 * iOS asserts `ExchangedFiat.forViewer` against too. A failure here is either a real regression or
 * a cross-platform decision that has to be made in the canonical fixture and re-synced to both
 * platforms — never a local edit.
 *
 * USDF cases go through [LocalFiat.forViewer]; the rest also go through the plain [Fiat.forViewer],
 * which must agree with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ViewerAmountVectorsTest {

    private val otherMint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    @Test
    fun `viewer amounts match the fixture`() {
        val fixture = JSONObject(
            javaClass.classLoader!!.getResourceAsStream("viewer_amount.json")!!
                .bufferedReader().use { it.readText() }
        )
        val minorDigits = fixture.getJSONObject("minorDigits")
        val cases = fixture.getJSONArray("cases")
        assertTrue(cases.length() > 0, "viewer_amount.json loaded no cases")

        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")

            val ratesJson = case.getJSONObject("rates")
            val rates = ratesJson.keys().asSequence().associate { code ->
                val currency = CurrencyCode.valueOf(code)
                currency to Rate(fx = ratesJson.getString(code).toDouble(), currency = currency)
            }
            val display = CurrencyCode.valueOf(case.getString("display"))
            val preferredRate = rates.getValue(display)

            val nativeJson = case.getJSONObject("native")
            val native = Fiat(
                fiat = nativeJson.getString("amount").toDouble(),
                currencyCode = CurrencyCode.valueOf(nativeJson.getString("currency")),
            )

            val expected = case.getJSONObject("expected")
            val expectedViewer = expected.getJSONObject("viewer")
            val expectedCurrency = CurrencyCode.valueOf(expectedViewer.getString("currency"))
            val expectedAmount = BigDecimal(expectedViewer.getString("amount"))
            val converted = expected.getBoolean("converted")

            fun check(label: String, actual: Fiat) {
                val digits = minorDigits.getInt(actual.currencyCode.name)
                val rounded = BigDecimal(actual.decimalValue).setScale(digits, RoundingMode.HALF_EVEN)
                assertEquals(expectedCurrency, actual.currencyCode, "`$name` ($label): currency")
                assertEquals(0, expectedAmount.compareTo(rounded), "`$name` ($label): $rounded != $expectedAmount")
                assertEquals(converted, actual.currencyCode != native.currencyCode, "`$name` ($label): converted")
            }

            if (case.getString("mint") == "usdf") {
                val settled = Fiat(case.getString("settledUsd").toDouble(), CurrencyCode.USD)
                check("LocalFiat", LocalFiat(usdf = settled, nativeAmount = native).forViewer(preferredRate, rates))
            } else {
                val amount = LocalFiat(usdf = Fiat(1_234_567L), nativeAmount = native, mint = otherMint)
                check("LocalFiat", amount.forViewer(preferredRate, rates))
                check("Fiat", native.forViewer(preferredRate, rates))
            }
        }
    }
}
