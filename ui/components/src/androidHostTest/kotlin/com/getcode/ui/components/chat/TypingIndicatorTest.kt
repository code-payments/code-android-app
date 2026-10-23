package com.getcode.ui.components.chat

import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class Typist(val id: String, val name: String?)

    // A typist can appear before their profile resolves, and the resolved profile arrives as the
    // same typist (same key) with more to draw. The avatar has to pick that up.
    @Test
    fun avatarRedrawsWhenATypistChangesUnderTheSameKey() {
        var typists by mutableStateOf(listOf(Typist("a", name = null)))
        composeTestRule.setContent {
            DesignSystem {
                TypingIndicator(
                    typists = typists,
                    key = { it.id },
                    avatar = { Text(it.name ?: "fallback") },
                )
            }
        }
        composeTestRule.onNodeWithText("fallback").assertExists()

        typists = listOf(Typist("a", name = "Alice"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Alice").assertExists()
        composeTestRule.onNodeWithText("fallback").assertDoesNotExist()
    }
}
