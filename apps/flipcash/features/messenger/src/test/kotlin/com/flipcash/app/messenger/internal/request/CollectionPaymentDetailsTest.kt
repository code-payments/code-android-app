package com.flipcash.app.messenger.internal.request

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.ResourceCollectionFundingStrings
import com.flipcash.app.tokens.planCollectionFunding
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.theme.DesignSystem
import com.getcode.util.resources.AndroidResources
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A funded collection's fee line and Payment Details draw exactly the strings in the shared
 * vectors' `display` block (group-collections spec, "Fee display"), for frames 5.2/5.3, 6.4/6.5
 * and 9.2/9.3. The expected strings are read from the fixture, not retyped here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class CollectionPaymentDetailsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val strings = ResourceCollectionFundingStrings(AndroidResources(RuntimeEnvironment.getApplication()))

    /** The fixture CollectionFundingVectorsTest runs; unit tests run from the module directory. */
    private val fixture: JSONObject = File(FIXTURE).let {
        assertTrue("missing ${it.absolutePath}", it.exists())
        JSONObject(it.readText())
    }

    private fun case(name: String): JSONObject =
        fixture.getJSONArray("cases").objects().first { it.getString("name") == name }

    private fun token(name: String, seed: Int): Token = mockk(relaxed = true) {
        every { address } returns Mint(List(32) { seed.toByte() })
        every { this@mockk.name } returns name
    }

    private fun ui(case: JSONObject): CollectionFundingUi {
        val input = case.getJSONObject("input")
        val balances = input.getJSONArray("balances").objects()
        val tokens = balances.withIndex().associate { (i, b) -> b.getString("id") to token(b.getString("name"), i + 1) }
        val plan = planCollectionFunding(
            amountMicros = input.getLong("amount_micros"),
            minorDigits = input.getInt("minor_digits"),
            minTopUpMicros = input.getLong("min_top_up_micros"),
            dollarsId = "dollars",
            selected = input.optString("selected").takeIf { !input.isNull("selected") },
            lastPaid = input.optString("last_paid").takeIf { !input.isNull("last_paid") },
            balances = balances.map { FundingBalance(it.getString("id"), it.getLong("fiat_micros"), it.getInt("sell_fee_bps")) },
        )
        return collectionFundingUi(
            plan = plan,
            amount = Fiat(quarks = input.getLong("amount_micros"), currencyCode = CurrencyCode.USD),
            tokenOf = tokens::get,
            nameOf = { it.name },
            topUpSourceName = "Google Pay",
            topUpFootnote = FOOTNOTE,
            strings = strings,
        )
    }

    private fun showSheet(
        funding: CollectionFundingUi?,
        isFunding: Boolean = true,
        finishing: Boolean = false,
        onAddAndPay: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = RequestPayFlow.State(
                        requestId = List(16) { 9 },
                        open = true,
                        amount = Fiat(10.0, CurrencyCode.USD),
                        creator = List(16) { 1 },
                        funding = isFunding,
                        finishing = finishing,
                    ),
                    members = emptyMap(),
                    token = null,
                    actions = SwipePayActions(onConfirm = {}, onDismiss = {}),
                    funding = funding,
                    onAddAndPay = onAddAndPay,
                )
            }
        }
    }

    /** Draws [name]'s modal, opens Payment Details from its ⓘ, and checks both against the fixture. */
    private fun assertDrawsCase(name: String): List<List<String>> {
        val case = case(name)
        val display = case.getJSONObject("display")
        showSheet(ui(case))

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(display.getString("modal_fee")).assertExists()
        composeTestRule.onNodeWithContentDescription("Payment Details").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithText("Payment Details").assertExists()
        composeTestRule.onNodeWithText("You’ll Pay").assertExists()
        composeTestRule.onNodeWithText("You’ll Use").assertExists()

        val details = display.getJSONObject("details")
        val pay = details.getJSONObject("pay")
        assertEquals(
            listOf(listOf(pay.getString("name"), pay.getString("amount"))),
            composeTestRule.onAllNodesWithTag(PAYMENT_DETAILS_PAY_ROW_TAG).texts(),
        )
        val use = details.getJSONArray("use").objects().map { row ->
            listOf(row.getString("name")) + row.getJSONArray("sublabels").strings() + row.getString("amount")
        }
        assertEquals(use, composeTestRule.onAllNodesWithTag(PAYMENT_DETAILS_USE_ROW_TAG).texts())
        val total = details.getJSONObject("total")
        assertEquals(
            listOf(listOf(total.getString("label"), total.getString("amount"))),
            composeTestRule.onAllNodesWithTag(PAYMENT_DETAILS_TOTAL_TAG).texts(),
        )
        return use
    }

    @Test
    fun `5_2 and 5_3 - Dollars then Jeffy with its conversion fee`() {
        assertDrawsCase("s5-s6-convert-shortfall")
    }

    @Test
    fun `6_4 and 6_5 - paying in Jeffy uses Jeffy first and draws no fee`() {
        assertDrawsCase("s6-pay-in-jeffy")
    }

    @Test
    fun `9_2 and 9_3 - twenty-nine currencies, largest sold first`() {
        val use = assertDrawsCase("s9-many-small-balances")

        assertEquals(29, use.size)
        val cents = use.map { row -> row.last().removePrefix("$").replace(".", "").toLong() }
        assertEquals(cents.sortedDescending(), cents)
    }

    @Test
    fun `back closes Payment Details and leaves the pay sheet up`() {
        showSheet(ui(case("s5-s6-convert-shortfall")))
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertExists()

        // The title bar also composes an unplaced back arrow that only reserves width.
        composeTestRule.onAllNodesWithTag("action_back").filterToOne(placed).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertExists()
    }

    /**
     * Frames 1, 2, 4.2, 7.2 and 8.2: the short state takes the swipe's place with the vector's
     * drawn short line and button, the Google Pay minimum under it, and the fee line of the plan
     * after the top-up (none for 4.2 and 7.2, "plus ~$0.04" for 8.2).
     */
    private fun assertDrawsShort(name: String) {
        val display = case(name).getJSONObject("display")
        val ui = ui(case(name))
        assertEquals(display.getString("short_by"), ui.short?.shortBy)
        assertEquals(display.getString("add_button"), ui.short?.addButton)
        assertEquals(display.optString("modal_fee").takeIf { !display.isNull("modal_fee") }, ui.modalFee)

        var taps = 0
        showSheet(ui, onAddAndPay = { taps++ })
        composeTestRule.onNodeWithText(display.getString("short_by")).assertExists()
        composeTestRule.onNodeWithText(FOOTNOTE).assertExists()
        composeTestRule.onNodeWithTag("request_pay_add").assertExists()
        composeTestRule.onNodeWithText(display.getString("add_button")).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, taps)
        if (ui.modalFee == null) {
            composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertDoesNotExist()
        } else {
            composeTestRule.onNodeWithText(ui.modalFee!!).assertExists()
        }
    }

    @Test
    fun `1 - an empty wallet shows the short state for the whole amount`() {
        assertDrawsShort("s1-no-balance")
    }

    @Test
    fun `2 - under the minimum, the button adds the minimum`() {
        assertDrawsShort("s2-min-top-up")
    }

    @Test
    fun `4_2 - Dollars short of the amount, the button adds $5 and pays $10`() {
        assertDrawsShort("s4-add-then-pay")
    }

    @Test
    fun `7_2 - short by $2_04, the button adds $5 and pays $11 with no fee line`() {
        assertDrawsShort("s7-short-under-min")
    }

    @Test
    fun `8_2 - short over the minimum, the button adds $12_04 with the post-top-up fee`() {
        assertDrawsShort("s8-short-over-min")
    }

    @Test
    fun `7_3 - Payment Details before the top-up show the plan after it`() {
        val display = case("s7-short-under-min").getJSONObject("display")
        val details = ui(case("s7-short-under-min")).details
        val expected = display.getJSONObject("details")

        assertEquals(expected.getJSONObject("pay").getString("name"), details.pay.row.name)
        assertEquals(expected.getJSONObject("pay").getString("amount"), details.pay.row.amount)
        assertEquals(
            expected.getJSONArray("use").objects().map { row ->
                listOf(row.getString("name")) +
                    row.getJSONArray("sublabels").strings().map { it.replace(" from Apple Pay", " from Google Pay") } +
                    row.getString("amount")
            },
            details.use.map { listOf(it.row.name) + it.row.sublabels + it.row.amount },
        )
        assertEquals(expected.getJSONObject("total").getString("amount"), details.total.amount)
    }

    @Test
    fun `a plan the balances cover has no short state`() {
        assertNull(ui(case("s5-s6-convert-shortfall")).short)
        showSheet(ui(case("s5-s6-convert-shortfall")))
        composeTestRule.onNodeWithTag("request_pay_add").assertDoesNotExist()
    }

    @Test
    fun `1_3 and 4_4 - Finishing Payment replaces the sheet while the top-up lands`() {
        showSheet(ui(case("s4-add-then-pay")), finishing = true)

        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()
        composeTestRule.onNodeWithText("Finishing Payment").assertExists()
        composeTestRule.onNodeWithText("This usually takes about 10 seconds").assertExists()
        composeTestRule.onNodeWithTag("request_pay_add").assertDoesNotExist()
    }

    @Test
    fun `a sheet that is not funding a collection draws no fee line`() {
        showSheet(ui(case("s5-s6-convert-shortfall")), isFunding = false)

        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertDoesNotExist()
    }

    private val placed = SemanticsMatcher("is placed") { it.layoutInfo.isPlaced }

    private fun SemanticsNodeInteractionCollection.texts(): List<List<String>> =
        fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

    private companion object {
        const val FIXTURE = "../../shared/tokens/src/androidHostTest/resources/collection_funding.json"
        const val FOOTNOTE = "The minimum you can add with Google Pay is $5."
    }
}
