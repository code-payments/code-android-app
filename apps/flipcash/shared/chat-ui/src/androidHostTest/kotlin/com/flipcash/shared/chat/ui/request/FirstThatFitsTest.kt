package com.flipcash.shared.chat.ui.request

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/** The deck's pill reads the status line's intrinsic width, so the line must be able to give one. */
@RunWith(RobolectricTestRunner::class)
class FirstThatFitsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val full = AnnotatedString("You owe $5.00 · $5.00 of $15.00")
    private val short = AnnotatedString("You owe $5.00")

    @Test
    fun `reports an intrinsic width when asked for one`() {
        var intrinsic = -1
        composeTestRule.setContent {
            FirstThatFits(
                full = full,
                short = short,
                style = TextStyle.Default,
                color = Color.White,
                modifier = Modifier.layout { measurable, constraints ->
                    intrinsic = measurable.maxIntrinsicWidth(constraints.maxHeight)
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            )
        }
        composeTestRule.waitForIdle()
        assertTrue(intrinsic > 0)
        composeTestRule.onNodeWithText(full.text).assertIsDisplayed()
    }

    @Test
    fun `falls back to the short line when the full one does not fit`() {
        composeTestRule.setContent {
            FirstThatFits(full, short, TextStyle.Default, Color.White, Modifier.width(1.dp))
        }
        composeTestRule.onNodeWithText(full.text).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(short.text).assertIsDisplayed()
    }
}
