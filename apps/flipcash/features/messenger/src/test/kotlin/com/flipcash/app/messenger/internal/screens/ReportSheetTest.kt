package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every row but one is the whole interaction: the tap is the answer. [ReportReason.Other] is the
 * exception, and the cases below are what that exception costs — it reveals instead of
 * submitting, it carries what was typed, it will not submit nothing, and, because it is the only
 * row that goes somewhere, it is the only one that has to offer a way back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ReportSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var submitted: Pair<ReportReason, String?>? = null

    /**
     * The editable node itself, reached by its set-text action rather than by tag: `TextInput`
     * puts the caller's modifier on the box it draws around the field, and hands the field a
     * modifier of its own making, so the focus and text semantics are on a node no tag can name.
     * The sheet has one field, so the action identifies it.
     */
    private fun AndroidComposeTestRule<*, *>.detailsField() = onNode(hasSetTextAction())

    /**
     * The back arrow, of which the semantics tree holds two: [TopAppBarBase] subcomposes its
     * leading slot a second time as a `leftIcon_probe`, purely to measure whether a real control
     * is there, and that copy is never placed. The placed one is subcomposed first.
     */
    private fun AndroidComposeTestRule<*, *>.backArrow() =
        onAllNodesWithTag("action_report_back").onFirst()

    private fun showSheet() {
        composeTestRule.setContent {
            FlipcashPreview {
                ReportSheet(
                    onSubmit = { reason, details -> submitted = reason to details },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `a plain reason submits on the tap, with no details`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Spam").performClick()

        assertEquals(ReportReason.Spam, submitted?.first)
        assertNull(submitted?.second)
    }

    @Test
    fun `Other reveals the field instead of submitting`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()

        assertNull(submitted)
        composeTestRule.detailsField().assertIsDisplayed()
    }

    @Test
    fun `Other submits the typed details`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.detailsField().performTextInput("they keep doing the thing")
        composeTestRule.onNodeWithTag("action_submit_report").performClick()

        assertEquals(ReportReason.Other, submitted?.first)
        assertEquals("they keep doing the thing", submitted?.second)
    }

    @Test
    fun `Other will not submit an empty field`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.onNodeWithTag("action_submit_report").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("action_submit_report").performClick()

        assertNull(submitted)
    }

    @Test
    fun `the reason list has nothing to go back to`() {
        showSheet()

        composeTestRule.onAllNodesWithTag("action_report_back").assertCountEquals(0)
    }

    @Test
    fun `the details step goes back to the reasons`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.backArrow().performClick()

        composeTestRule.onNodeWithTag("action_report_reason_Spam").assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun `going back keeps what was typed`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.detailsField().performTextInput("half a thought")
        composeTestRule.backArrow().performClick()
        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()

        composeTestRule.onNodeWithTag("action_submit_report").performClick()
        assertEquals("half a thought", submitted?.second)
    }

    private val expandLabel: String
        get() = composeTestRule.activity.getString(R.string.action_expandReportDetails)

    private val collapseLabel: String
        get() = composeTestRule.activity.getString(R.string.action_collapseReportDetails)

    private fun pressBack() = composeTestRule.runOnUiThread {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun `the field offers a way to go full screen`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()

        composeTestRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()
    }

    @Test
    fun `one control both expands and collapses`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.onNodeWithTag("action_report_expand").performClick()
        composeTestRule.onNodeWithContentDescription(collapseLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("action_report_expand").performClick()
        composeTestRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()
    }

    /**
     * Expanding is a step of its own, so back unwinds it before the step that contains it. A back
     * that jumped straight to the reasons would throw away a draft the user was mid-sentence in.
     */
    @Test
    fun `back leaves full screen before it leaves the details step`() {
        showSheet()

        composeTestRule.onNodeWithTag("action_report_reason_Other").performClick()
        composeTestRule.detailsField().performTextInput("still typing")
        composeTestRule.onNodeWithTag("action_report_expand").performClick()

        pressBack()
        composeTestRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()

        pressBack()
        composeTestRule.onNodeWithTag("action_report_reason_Spam").assertIsDisplayed()
    }
}
