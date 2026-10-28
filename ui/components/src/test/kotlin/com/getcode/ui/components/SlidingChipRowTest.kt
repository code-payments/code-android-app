package com.getcode.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

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
}
