package com.getcode.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Wide enough for the widest row the tests lay out.
@Config(qualifiers = "w411dp-h891dp")
// Measures real text, so the fit test sees the labels' true widths.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SlidingChipRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val options = listOf("1 day", "7 days", "30 days")

    @Test
    fun `the selected chip is selected and the others are not`() {
        composeTestRule.setContent {
            DesignSystem {
                SlidingChipRow(options = options, selected = "7 days", onSelect = {}, label = { it })
            }
        }
        composeTestRule.onNodeWithText("7 days").assertIsSelected()
        composeTestRule.onNodeWithText("1 day").assertIsNotSelected()
        composeTestRule.onNodeWithText("30 days").assertIsNotSelected()
    }

    @Test
    fun `each chip is a radio button`() {
        composeTestRule.setContent {
            DesignSystem {
                SlidingChipRow(options = options, selected = "1 day", onSelect = {}, label = { it })
            }
        }
        composeTestRule.onNodeWithText("30 days")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `tapping a chip selects it`() {
        val picks = mutableListOf<String>()
        composeTestRule.setContent {
            DesignSystem {
                var selected by remember { mutableStateOf("1 day") }
                SlidingChipRow(
                    options = options,
                    selected = selected,
                    onSelect = { picks += it; selected = it },
                    label = { it },
                )
            }
        }
        composeTestRule.onNodeWithText("30 days").performClick()
        assertEquals(listOf("30 days"), picks)
        composeTestRule.onNodeWithText("30 days").assertIsSelected()
        composeTestRule.onNodeWithText("1 day").assertIsNotSelected()
    }

    @Test
    fun `no chip is selected for a choice none of them stands for`() {
        composeTestRule.setContent {
            DesignSystem {
                SlidingChipRow(options = options, selected = null, onSelect = {}, label = { it })
            }
        }
        options.forEach { composeTestRule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun `the chips share the row's width equally`() {
        composeTestRule.setContent {
            DesignSystem {
                SlidingChipRow(
                    options = listOf("1", "5", "10", "25", "No limit"),
                    selected = "1",
                    onSelect = {},
                    label = { it },
                    modifier = Modifier.width(371.dp),
                )
            }
        }
        val widths = listOf("1", "5", "10", "25", "No limit")
            .map { composeTestRule.onNodeWithText(it).getBoundsInRoot().let { b -> b.right - b.left } }
        // Equal to the pixel: the row's 347dp of chips doesn't divide by five exactly.
        assertTrue(widths.max() - widths.min() <= 1.dp, "chip widths $widths")
        // Five chips and four 6dp gaps fill the row.
        val filled = widths.reduce { a, b -> a + b } + 24.dp
        assertTrue(filled in 370.dp..372.dp, "row filled to $filled")
    }

    @Test
    fun `no label is truncated at font scales up to 2 on a 411dp or a 360dp phone`() {
        var case by mutableStateOf(Triple(labelSets.first(), 371, 1f))
        composeTestRule.setContent {
            val density = LocalDensity.current
            val (labels, rowWidth, fontScale) = case
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DesignSystem {
                    SlidingChipRow(
                        options = labels,
                        selected = labels.first(),
                        onSelect = {},
                        label = { it },
                        modifier = Modifier.width(rowWidth.dp),
                    )
                }
            }
        }
        // The row is the phone's width less the 20dp inset on each side.
        for (rowWidth in listOf(371, 320)) {
            for (fontScale in listOf(1f, 1.5f, 2f)) {
                for (labels in labelSets) {
                    case = Triple(labels, rowWidth, fontScale)
                    composeTestRule.waitForIdle()
                    val where = "in a ${rowWidth}dp row at font scale $fontScale"
                    labels.forEach { label ->
                        val layout = textLayout(label)
                        val shown = label.substring(0, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
                        val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
                        assertTrue(
                            shown == label && !layout.didOverflowHeight &&
                                widest <= layout.layoutInput.constraints.maxWidth,
                            "\"$label\" shows \"$shown\" $where",
                        )
                    }
                    val heights = labels.map { composeTestRule.onNodeWithText(it).getBoundsInRoot().let { b -> b.bottom - b.top } }
                    assertEquals(1, heights.distinct().size, "chip heights $heights $where")
                }
            }
        }
    }

    private val labelSets = listOf(
        listOf("1 day", "7 days", "30 days", "Never"),
        listOf("1", "5", "10", "25", "No limit"),
        listOf("1 Day", "3 Days", "1 Week", "2 Weeks", "4 Weeks"),
    )

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
}
