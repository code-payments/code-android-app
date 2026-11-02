package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.theme.DesignSystem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Which day a message's full send time names, and that the bubble speaks it after its text rather
 * than in place of it.
 */
@RunWith(RobolectricTestRunner::class)
class MessageTimeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val utc = TimeZone.UTC
    // 2026-10-23 18:13 UTC.
    private val now = Instant.parse("2026-10-23T18:13:00Z")

    @Test
    fun `earlier the same day is today`() {
        assertEquals(MessageDay.Today, MessageDay.of(Instant.parse("2026-10-23T00:01:00Z"), now, utc))
    }

    @Test
    fun `a send stamped after now is still today`() {
        assertEquals(MessageDay.Today, MessageDay.of(Instant.parse("2026-10-23T18:14:00Z"), now, utc))
    }

    @Test
    fun `the day before is yesterday`() {
        assertEquals(MessageDay.Yesterday, MessageDay.of(Instant.parse("2026-10-22T23:59:00Z"), now, utc))
    }

    @Test
    fun `older this year is a date without the year`() {
        assertEquals(
            MessageDay.Date(LocalDate(2026, 10, 20), withYear = false),
            MessageDay.of(Instant.parse("2026-10-20T12:00:00Z"), now, utc),
        )
    }

    @Test
    fun `an earlier year carries the year`() {
        assertEquals(
            MessageDay.Date(LocalDate(2025, 12, 31), withYear = true),
            MessageDay.of(Instant.parse("2025-12-31T12:00:00Z"), now, utc),
        )
    }

    @Test
    fun `the day is read in the given time zone`() {
        // 01:00 UTC on the 23rd is still the 22nd in New York.
        val newYork = TimeZone.of("America/New_York")
        assertEquals(
            MessageDay.Yesterday,
            MessageDay.of(Instant.parse("2026-10-23T01:00:00Z"), now, newYork),
        )
    }

    private fun bubble(content: MessageContent) = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = content,
        isFromSelf = true,
        timestamp = Clock.System.now() - 5.minutes,
    )

    private fun setMergedBubble(item: ChatListItem.ContentBubble) {
        composeTestRule.setContent {
            DesignSystem {
                // The row merges the bubble into its click target; this stands in for it.
                Box(Modifier.testTag("row").semantics(mergeDescendants = true) { }) {
                    ContentBubble(item = item, position = BubblePosition.Solo)
                }
            }
        }
    }

    @Test
    fun `the bubble speaks its time after its text`() {
        setMergedBubble(bubble(MessageContent.Text("hello")))

        composeTestRule.onNodeWithTag("row")
            .assertTextEquals("hello")
            .assert(
                SemanticsMatcher("state description is today's time") {
                    it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }?.startsWith("Today, ") == true
                },
            )
    }

    @Test
    fun `a deleted message has no time to speak`() {
        setMergedBubble(bubble(MessageContent.Deleted(Clock.System.now(), deletedBy = null)))

        composeTestRule.onNodeWithTag("row")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
