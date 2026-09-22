package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportDescription
import com.flipcash.reporting.ReportReason
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ReportFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var chosen: ReportReason? = null
    private var submitted: String? = null
    private var dismissed = false

    private fun showReasons() {
        composeTestRule.setContent {
            FlipcashPreview {
                ReasonSelectionContent(
                    onChoose = { chosen = it },
                    onNavigateUp = { dismissed = true },
                )
            }
        }
    }

    private fun showDetails(state: TextFieldState = TextFieldState()) {
        composeTestRule.setContent {
            FlipcashPreview {
                ReportDetailsContent(
                    state = state,
                    onSubmit = { submitted = it },
                    onNavigateUp = { dismissed = true },
                )
            }
        }
    }

    // `TopAppBarBase` subcomposes the leading slot more than once: once to measure and place it,
    // once as a probe to tell a real control from an empty slot, and — for a centred title — a
    // phantom back arrow that only reserves width. Every copy carries the icon's test tag, so the
    // tag alone says nothing about what is on screen. Only the real control is ever placed.
    private val placed = SemanticsMatcher("is placed") { it.layoutInfo.isPlaced }

    private fun leadingControl(tag: String) =
        composeTestRule.onAllNodesWithTag(tag).filterToOne(placed)

    private fun assertNoLeadingControl(tag: String) =
        composeTestRule.onAllNodesWithTag(tag).filter(placed).assertCountEquals(0)

    @Test
    fun `offers every reason in the shared vocabulary`() {
        showReasons()

        for (reason in ReportReason.entries) {
            composeTestRule.onNodeWithTag("action_report_reason_${reason.name}").assertExists()
        }
    }

    @Test
    fun `says what each reason covers`() {
        showReasons()

        for (reason in ReportReason.entries) {
            composeTestRule
                .onNodeWithText(composeTestRule.activity.getString(reason.descriptionRes))
                .assertExists()
        }
    }

    @Test
    fun `picking a row is not yet an answer`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Harassment.name}")
            .performClick()

        assertNull(chosen)
    }

    @Test
    fun `will not continue before a reason is picked`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_continue").assertIsNotEnabled()
    }

    @Test
    fun `answers with the reason that was picked`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Harassment.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_continue").performClick()

        assertEquals(ReportReason.Harassment, chosen)
    }

    @Test
    fun `answers with the last row picked, not the first`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Spam.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Violence.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_continue").performClick()

        assertEquals(ReportReason.Violence, chosen)
    }

    @Test
    fun `answers with Other rather than deciding what it costs`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Other.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_continue").performClick()

        assertEquals(ReportReason.Other, chosen)
    }

    @Test
    fun `calls the button what it will do`() {
        showReasons()

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Spam.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_continue")
            .assertTextEquals(
                composeTestRule.activity.getString(R.string.action_submitReport)
            )

        composeTestRule.onNodeWithTag("action_report_reason_${ReportReason.Other.name}")
            .performClick()
        composeTestRule.onNodeWithTag("action_report_reason_continue")
            .assertTextEquals(
                composeTestRule.activity.getString(com.flipcash.core.R.string.action_next)
            )
    }

    @Test
    fun `will not submit an empty description`() {
        showDetails()

        composeTestRule.onNodeWithTag("action_report_submit").assertIsNotEnabled()
    }

    @Test
    fun `submits the description without its surrounding whitespace`() {
        showDetails()

        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("  they kept messaging me  ")
        composeTestRule.onNodeWithTag("action_report_submit").performClick()

        assertEquals("they kept messaging me", submitted)
    }

    @Test
    fun `counts down the room left in the description`() {
        showDetails()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("hello")

        composeTestRule.onNodeWithTag("report_details_counter")
            .assertContentDescriptionEquals("${ReportDescription.MAX_DETAILS_LENGTH - 5}")
    }

    @Test
    fun `refuses a description that is over the limit`() {
        showDetails(TextFieldState("x".repeat(ReportDescription.MAX_DETAILS_LENGTH + 1)))

        composeTestRule.onNodeWithTag("action_report_submit").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("action_report_submit").performClick()
        assertNull(submitted)
    }

    @Test
    fun `submits once there is something to send`() {
        showDetails()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("spamming links")

        composeTestRule.onNodeWithTag("action_report_submit").assertIsEnabled()
    }

    @Test
    fun `leaves the first step by closing, not by going back`() {
        showReasons()

        leadingControl("action_close").assertExists()
        assertNoLeadingControl("action_back")
    }

    @Test
    fun `closing the first step asks to leave the flow`() {
        showReasons()

        leadingControl("action_close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `leaves the details step by going back, not by closing`() {
        showDetails()

        leadingControl("action_back").assertExists()
        assertNoLeadingControl("action_close")
    }
}
