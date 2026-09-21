package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.flipcash.app.theme.FlipcashPreview
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
 * exception, and the three cases below are what that exception costs — it reveals instead of
 * submitting, it carries what was typed, and it will not submit nothing.
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
}
