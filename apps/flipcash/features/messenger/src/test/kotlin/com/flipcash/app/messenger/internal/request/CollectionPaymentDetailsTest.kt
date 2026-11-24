package com.flipcash.app.messenger.internal.request

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.flipcash.app.messenger.internal.swipepay.SWIPE_PAY_PAGES_TAG
import com.flipcash.app.messenger.internal.swipepay.SWIPE_PAY_SHEET_TAG
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.ResourceCollectionFundingStrings
import com.flipcash.app.tokens.planCollectionFunding
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.theme.DesignSystem
import com.getcode.util.resources.AndroidResources
import com.getcode.view.LoadingSuccessState
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
        progress: LoadingSuccessState = LoadingSuccessState(),
        onAddAndPay: () -> Unit = {},
        onDismiss: () -> Unit = {},
        host: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ) {
        composeTestRule.setContent {
            DesignSystem {
                host {
                    RequestPaySheet(
                        pay = collectionPay(isFunding = isFunding, finishing = finishing, progress = progress),
                        members = emptyMap(),
                        token = null,
                        actions = SwipePayActions(onConfirm = {}, onDismiss = onDismiss),
                        funding = funding,
                        onAddAndPay = onAddAndPay,
                    )
                }
            }
        }
    }

    /** The flow's state for an open collection sheet of $10.00. */
    private fun collectionPay(
        isFunding: Boolean = true,
        finishing: Boolean = false,
        progress: LoadingSuccessState = LoadingSuccessState(),
    ) = RequestPayFlow.State(
        requestId = List(16) { 9 },
        open = true,
        amount = Fiat(10.0, CurrencyCode.USD),
        creator = List(16) { 1 },
        funding = isFunding,
        finishing = finishing,
        progress = progress,
    )

    /** One sheet over [pay] and [funding], which a test changes in place as the flow does. */
    private fun showFlowSheet(
        pay: () -> RequestPayFlow.State,
        funding: () -> CollectionFundingUi?,
        onConfirm: () -> Unit = {},
        onAddAndPay: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = pay(),
                    members = emptyMap(),
                    token = null,
                    actions = SwipePayActions(onConfirm = onConfirm, onDismiss = {}),
                    funding = funding(),
                    onAddAndPay = onAddAndPay,
                )
            }
        }
    }

    /** [case] with [dollarsMicros] in Dollars and no other balance: the plan once a top-up landed. */
    private fun withDollars(case: JSONObject, dollarsMicros: Long): JSONObject = JSONObject(case.toString()).apply {
        getJSONObject("input").put(
            "balances",
            JSONArray().put(
                JSONObject()
                    .put("id", "dollars").put("name", "Dollars").put("kind", "dollars")
                    .put("fiat_micros", dollarsMicros).put("sell_fee_bps", 0),
            ),
        )
    }

    /**
     * With the clock held, steps frames until [to] is drawn, and checks [from] is still drawn on
     * that frame: the sheet fades one into the other rather than swapping them.
     */
    private fun assertFades(from: String, to: String, maxFrames: Int = 10) {
        composeTestRule.mainClock.autoAdvance = false
        var frames = 0
        while (composeTestRule.onAllNodesWithTag(to).fetchSemanticsNodes().isEmpty() && frames < maxFrames) {
            composeTestRule.mainClock.advanceTimeByFrame()
            frames++
        }
        composeTestRule.onNodeWithTag(to).assertExists()
        composeTestRule.onNodeWithTag(from).assertExists()
    }

    /** Steps [frames] frames with the clock held, checking the sheet is up on each. */
    private fun assertSheetUpEachFrame(frames: Int = 40) {
        composeTestRule.mainClock.autoAdvance = false
        repeat(frames) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
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

    /** 7.2 draws no fee line, so its short line's ⓘ is the only way to 7.3. */
    @Test
    fun `7_2 to 7_3 - a tap on the short line opens Payment Details`() {
        val display = case("s7-short-under-min").getJSONObject("display")
        showSheet(ui(case("s7-short-under-min")))
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()

        // On the line's text, not the ⓘ: the whole line takes the tap.
        composeTestRule.onNodeWithText(display.getString("short_by")).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertExists()
        val use = display.getJSONObject("details").getJSONArray("use").objects().map { row ->
            listOf(row.getString("name")) +
                row.getJSONArray("sublabels").strings().map { it.replace(" from Apple Pay", " from Google Pay") } +
                row.getString("amount")
        }
        assertEquals(use, composeTestRule.onAllNodesWithTag(PAYMENT_DETAILS_USE_ROW_TAG).texts())
    }

    @Test
    fun `the short line reads to accessibility as the fee line does`() {
        showSheet(ui(case("s8-short-over-min")))

        for (tag in listOf(COLLECTION_FEE_LINE_TAG, COLLECTION_SHORT_LINE_TAG)) {
            val line = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertEquals(tag, "Payment Details", line.getOrNull(SemanticsActions.OnClick)?.label)
            assertEquals(tag, Role.Button, line.getOrNull(SemanticsProperties.Role))
            assertEquals(tag, listOf("Payment Details"), line.getOrNull(SemanticsProperties.ContentDescription))
        }
    }

    /** 8.2 has both lines: each keeps its ⓘ, and both open the same Payment Details. */
    @Test
    fun `8_2 - both lines keep their info and open the same Payment Details`() {
        showSheet(ui(case("s8-short-over-min")))
        assertEquals(2, composeTestRule.onAllNodesWithContentDescription("Payment Details").fetchSemanticsNodes().size)

        val opened = listOf(COLLECTION_FEE_LINE_TAG, COLLECTION_SHORT_LINE_TAG).map { tag ->
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertExists()
            val rows = composeTestRule.onAllNodesWithTag(PAYMENT_DETAILS_USE_ROW_TAG).texts()

            composeTestRule.onAllNodesWithTag("action_back").filterToOne(placed).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
            rows
        }
        assertTrue(opened.first().isNotEmpty())
        assertEquals(opened.first(), opened.last())
    }

    /** Frames 1.1 and 2.1: the Request screen's button reads "Pay $10.00" only when the top-up is the amount. */
    @Test
    fun `the top-up is the amount only in section 1`() {
        assertEquals(true, ui(case("s1-no-balance")).short?.topUpIsAmount)
        for (name in listOf("s2-min-top-up", "s4-add-then-pay", "s7-short-under-min", "s8-short-over-min")) {
            assertEquals(name, false, ui(case(name)).short?.topUpIsAmount)
        }
    }

    @Test
    fun `a plan the balances cover has no short state`() {
        assertNull(ui(case("s5-s6-convert-shortfall")).short)
        showSheet(ui(case("s5-s6-convert-shortfall")))
        composeTestRule.onNodeWithTag("request_pay_add").assertDoesNotExist()
    }

    @Test
    fun `1_3 and 4_4 - Finishing Payment takes the sheet's content while the top-up lands`() {
        showSheet(ui(case("s4-add-then-pay")), finishing = true)

        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()
        composeTestRule.onNodeWithText("Finishing Payment").assertExists()
        composeTestRule.onNodeWithText("This usually takes about 10 seconds").assertExists()
        composeTestRule.onNodeWithTag("request_pay_add").assertDoesNotExist()
    }

    /**
     * Frames 1.3 to 3.3 in one sheet: Finishing Payment, the checkmark, then the slide down. The
     * sheet is up on every frame between them, so nothing closes and reopens. The flow resets
     * when the hold ends, and section 1's balances are short again once the payment spent the
     * Dollars: the sheet slides down on the checkmark it held, with no short line.
     */
    @Test
    fun `1_3 to 3_3 - Finishing Payment, the checkmark and the slide down are one sheet`() {
        val atCharge = ui(case("s1-no-balance"))
        val paidWith = ui(withDollars(case("s1-no-balance"), 10_000_000L))
        assertNull(paidWith.short)
        var pay by mutableStateOf(collectionPay(finishing = true, progress = LoadingSuccessState(loading = true)))
        var funding by mutableStateOf<CollectionFundingUi?>(atCharge)
        showFlowSheet(pay = { pay }, funding = { funding })
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()

        // One frame in, both are drawn as Finishing Payment fades to the checkmark: no hard swap.
        composeTestRule.mainClock.autoAdvance = false
        pay = collectionPay(progress = LoadingSuccessState(success = true))
        funding = paidWith
        assertFades(from = "request_pay_finishing", to = "request_pay_swipe")
        assertSheetUpEachFrame()
        composeTestRule.onNodeWithTag("request_pay_finishing").assertDoesNotExist()
        composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()
        composeTestRule.onNodeWithTag(COLLECTION_SHORT_LINE_TAG).assertDoesNotExist()

        // The hold ends and dismiss() resets the flow: the sheet slides down on the frame it held.
        composeTestRule.mainClock.autoAdvance = false
        pay = RequestPayFlow.State()
        funding = atCharge
        repeat(2) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
            composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()
            composeTestRule.onNodeWithTag(COLLECTION_SHORT_LINE_TAG).assertDoesNotExist()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertDoesNotExist()
    }

    /** Frames 4.3 to 4.4: Google Pay's spinner on Add and Pay fades to Finishing Payment, in the same sheet. */
    @Test
    fun `4_4 - Add and Pay's spinner fades to Finishing Payment in the same sheet`() {
        var pay by mutableStateOf(collectionPay(progress = LoadingSuccessState(loading = true)))
        showFlowSheet(pay = { pay }, funding = { ui(case("s4-add-then-pay")) })
        composeTestRule.onNodeWithTag("request_pay_add").assertExists()

        composeTestRule.mainClock.autoAdvance = false
        pay = collectionPay(finishing = true, progress = LoadingSuccessState(loading = true))
        assertFades(from = "request_pay_add", to = "request_pay_finishing")
        assertSheetUpEachFrame()

        composeTestRule.onNodeWithTag("request_pay_add").assertDoesNotExist()
        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()
    }

    /**
     * The flow can close the sheet a few frames after opening it: a top-up that stops right after
     * the charge. The sheet slides down from wherever it got to, and the next open draws a new one.
     */
    @Test
    fun `a sheet closed while it is still opening slides down, and the next open draws again`() {
        composeTestRule.mainClock.autoAdvance = false
        var pay by mutableStateOf(collectionPay(finishing = true, progress = LoadingSuccessState(loading = true)))
        showFlowSheet(pay = { pay }, funding = { ui(case("s1-no-balance")) })
        repeat(2) { composeTestRule.mainClock.advanceTimeByFrame() }
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()

        pay = RequestPayFlow.State()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertDoesNotExist()

        pay = collectionPay()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithTag("request_pay_add").assertExists()
    }

    /**
     * Holds the clock, closes the flow under the sheet, and steps two frames into the slide-down,
     * checking the sheet is still drawn with [tag] on it.
     */
    private fun closeIntoSlideDown(close: () -> Unit, tag: String) {
        composeTestRule.mainClock.autoAdvance = false
        close()
        repeat(2) { composeTestRule.mainClock.advanceTimeByFrame() }
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithTag(tag).assertExists()
    }

    /** Lets the slide-down finish, checks the sheet has gone, and opens the next sheet on [open]. */
    private fun finishSlideDownAndReopen(open: () -> Unit) {
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertDoesNotExist()
        open()
        composeTestRule.waitForIdle()
    }

    /** The flow has reset once its sheet slides down: a tap there must not start a top-up. */
    @Test
    fun `Add and Pay on a sheet sliding down reaches nothing, and the next sheet's reaches the flow`() {
        var adds = 0
        var pay by mutableStateOf(collectionPay())
        showFlowSheet(pay = { pay }, funding = { ui(case("s7-short-under-min")) }, onAddAndPay = { adds++ })
        composeTestRule.waitForIdle()

        closeIntoSlideDown(close = { pay = RequestPayFlow.State() }, tag = "request_pay_add")
        composeTestRule.onNodeWithTag("request_pay_add").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals("a tap on the closing sheet", 0, adds)

        finishSlideDownAndReopen { pay = collectionPay() }
        composeTestRule.onNodeWithTag("request_pay_add").performClick()
        composeTestRule.waitForIdle()
        assertEquals("a tap on the next sheet", 1, adds)
    }

    /** The finger's path: down on the track, a drag to its end, and up, while the sheet slides down. */
    @Test
    fun `a swipe on a sheet sliding down reaches nothing, and the next sheet's reaches the flow`() {
        var confirms = 0
        var pay by mutableStateOf(collectionPay(isFunding = false))
        showFlowSheet(pay = { pay }, funding = { null }, onConfirm = { confirms++ })
        composeTestRule.waitForIdle()

        closeIntoSlideDown(close = { pay = RequestPayFlow.State() }, tag = "request_pay_swipe")
        composeTestRule.onNodeWithTag("request_pay_swipe").performTouchInput {
            swipeRight(startX = left + 1f, endX = right, durationMillis = 120)
        }
        repeat(2) { composeTestRule.mainClock.advanceTimeByFrame() }
        assertEquals("a swipe on the closing sheet", 0, confirms)

        finishSlideDownAndReopen { pay = collectionPay(isFunding = false) }
        composeTestRule.onNodeWithTag("request_pay_swipe").performTouchInput {
            swipeRight(startX = left + 1f, endX = right, durationMillis = 120)
        }
        composeTestRule.waitForIdle()
        assertEquals("a swipe on the next sheet", 1, confirms)
    }

    /** Pay step 4: the 60 s deadline takes Finishing Payment back to the sheet's swipe, in the same sheet. */
    @Test
    fun `a payment not confirmed takes Finishing Payment back to the swipe in the same sheet`() {
        var pay by mutableStateOf(collectionPay(finishing = true, progress = LoadingSuccessState(loading = true)))
        showFlowSheet(pay = { pay }, funding = { ui(case("s5-s6-convert-shortfall")) })
        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()

        pay = collectionPay()
        assertSheetUpEachFrame()

        composeTestRule.onNodeWithTag("request_pay_finishing").assertDoesNotExist()
        composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()
    }

    @Test
    fun `back during Finishing Payment leaves the sheet up in Finishing Payment`() {
        var dismissals = 0
        showSheet(
            ui(case("s4-add-then-pay")),
            finishing = true,
            progress = LoadingSuccessState(loading = true),
            onDismiss = { dismissals++ },
        )

        systemBack()

        composeTestRule.onNodeWithTag(SWIPE_PAY_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithTag("request_pay_finishing").assertExists()
        assertEquals(0, dismissals)
    }

    @Test
    fun `a sheet that is not funding a collection draws no fee line`() {
        showSheet(ui(case("s5-s6-convert-shortfall")), isFunding = false)

        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertDoesNotExist()
    }

    // Payment Details is pushed inside the pay sheet (spec "Payment Details"): one sheet, the pay
    // content slid out and gone, and back slides it back.

    private val inPaySheet = hasAnyAncestor(hasTestTag(SWIPE_PAY_SHEET_TAG))

    private fun assertDetailsInsideThePaySheet() {
        composeTestRule.onAllNodesWithTag(SWIPE_PAY_SHEET_TAG).assertCountEquals(1)
        composeTestRule.onNode(hasTestTag(PAYMENT_DETAILS_SHEET_TAG) and inPaySheet).assertExists()
        // Nothing of the pay content is left to peek behind it.
        listOf("request_pay_swipe", "request_pay_add", COLLECTION_FEE_LINE_TAG, COLLECTION_SHORT_LINE_TAG)
            .forEach { composeTestRule.onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test
    fun `5_3 - the fee line's info pushes Payment Details inside the pay sheet`() {
        showSheet(ui(case("s5-s6-convert-shortfall")))
        composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()

        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        composeTestRule.waitForIdle()

        assertDetailsInsideThePaySheet()
    }

    @Test
    fun `7_3 - the short line's info pushes Payment Details inside the pay sheet`() {
        showSheet(ui(case("s7-short-under-min")))
        composeTestRule.onNodeWithTag("request_pay_add").assertExists()

        composeTestRule.onNodeWithTag(COLLECTION_SHORT_LINE_TAG).performClick()
        composeTestRule.waitForIdle()

        assertDetailsInsideThePaySheet()
    }

    /** The page's height differs from the pay content's; the sheet passes through between them. */
    @Test
    fun `the sheet resizes to Payment Details as it slides in`() {
        showSheet(ui(case("s9-many-small-balances")))
        val pages = composeTestRule.onNodeWithTag(SWIPE_PAY_PAGES_TAG)
        val before = pages.height()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        val frames = List(40) {
            composeTestRule.mainClock.advanceTimeByFrame()
            pages.height()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        val after = pages.height()

        assertNotEquals(before, after)
        assertTrue(
            "no frame between $before and $after: $frames",
            frames.any { it > minOf(before, after) && it < maxOf(before, after) },
        )
    }

    @Test
    fun `back slides the pay content back with the picked currency and amounts kept`() {
        val case = case("s5-s6-convert-shortfall")
        val funding = ui(case)
        val jeffy = token("Jeffy", 7)
        val bogey = token("Bogey", 8)
        var token by mutableStateOf<Token?>(jeffy)
        composeTestRule.setContent {
            DesignSystem {
                RequestPaySheet(
                    pay = RequestPayFlow.State(
                        requestId = List(16) { 9 },
                        open = true,
                        amount = Fiat(10.0, CurrencyCode.USD),
                        creator = List(16) { 1 },
                        funding = true,
                    ),
                    members = emptyMap(),
                    token = token,
                    actions = SwipePayActions(onConfirm = {}, onDismiss = {}),
                    collectionCurrencies = listOf(balance(jeffy, 0.0), balance(bogey, 4.0)),
                    onPickCollectionCurrency = { mint -> token = listOf(jeffy, bogey).first { it.address == mint } },
                    funding = funding,
                )
            }
        }
        composeTestRule.onNodeWithText("Jeffy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(collectionCurrencyRowTag(bogey.address)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Select Payment Currency").assertDoesNotExist()

        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bogey").assertDoesNotExist()
        composeTestRule.onAllNodesWithTag("action_back").filterToOne(placed).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNode(hasText("Bogey") and inPaySheet).assertExists()
        composeTestRule.onNodeWithText("$10.00").assertExists()
        composeTestRule.onNodeWithText(case.getJSONObject("display").getString("modal_fee")).assertExists()
        composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()
    }

    @Test
    fun `system back pops Payment Details first, then closes the sheet`() {
        var dismissals = 0
        showSheet(ui(case("s5-s6-convert-shortfall")), onDismiss = { dismissals++ })
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        composeTestRule.waitForIdle()

        systemBack()
        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).assertExists()
        assertEquals(0, dismissals)

        systemBack()
        assertEquals(1, dismissals)
    }

    /** The app hosts every scene in a lookahead scope, where a resizing sheet once looped (#1929). */
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun `Payment Details settles in and out inside a lookahead scope`() {
        showSheet(ui(case("s7-short-under-min")), host = { SharedTransitionLayout { it() } })

        composeTestRule.onNodeWithTag(COLLECTION_SHORT_LINE_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertExists()

        systemBack()
        composeTestRule.onNodeWithTag(COLLECTION_SHORT_LINE_TAG).assertExists()
    }

    @Test
    fun `Payment Details stays shut while the payment is in flight`() {
        showSheet(ui(case("s5-s6-convert-shortfall")), progress = LoadingSuccessState(loading = true))

        composeTestRule.onNodeWithTag(COLLECTION_FEE_LINE_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PAYMENT_DETAILS_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun `a sheet that is not funding a collection composes no page`() {
        showSheet(ui(case("s5-s6-convert-shortfall")), isFunding = false)

        composeTestRule.onNodeWithTag(SWIPE_PAY_PAGES_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag("request_pay_swipe").assertExists()
    }

    private fun systemBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.height(): Int = fetchSemanticsNode().size.height

    private fun balance(token: Token, usd: Double) = TokenWithLocalizedBalance(
        token = token,
        balance = LocalFiat(usdf = Fiat(usd, CurrencyCode.USD), nativeAmount = Fiat(usd, CurrencyCode.USD)),
    )

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
