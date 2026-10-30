package com.getcode.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `counts up to 99 read as the number`() {
        assertEquals("1", unreadCountLabel(1))
        assertEquals("99", unreadCountLabel(99))
    }

    @Test
    fun `counts over 99 read 99+`() {
        assertEquals("99+", unreadCountLabel(100))
        assertEquals("99+", unreadCountLabel(150))
    }

    @Test
    fun `badge shows 99 at 99`() = assertBadgeText(count = 99, expected = "99")

    @Test
    fun `badge caps at 99+ at 100`() = assertBadgeText(count = 100, expected = "99+")

    @Test
    fun `badge caps at 99+ at 150`() = assertBadgeText(count = 150, expected = "99+")

    // The chat row pill (`UnreadBadge`): an 18dp fixed height with 4dp side padding. At the cap it
    // must stay 18dp tall and wide enough to hold the whole label.
    @Test
    fun `99+ keeps the 18dp pill height and fits inside it`() {
        composeTestRule.setContent {
            DesignSystem {
                Badge(
                    modifier = Modifier.testTag("badge"),
                    count = 150,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    height = 18.dp,
                )
            }
        }
        composeTestRule.waitForIdle()

        val pill = composeTestRule.onNodeWithTag("badge").assertHeightIsEqualTo(18.dp).getBoundsInRoot()
        val label = composeTestRule.onNodeWithText("99+").getBoundsInRoot()
        assertTrue(label.left >= pill.left && label.right <= pill.right, "label $label overflows pill $pill")
        assertTrue(label.top >= pill.top && label.bottom <= pill.bottom, "label $label overflows pill $pill")
    }

    private fun assertBadgeText(count: Int, expected: String) {
        composeTestRule.setContent { DesignSystem { Badge(count = count) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(expected).assertExists()
    }
}
