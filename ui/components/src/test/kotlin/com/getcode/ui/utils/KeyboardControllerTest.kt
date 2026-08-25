package com.getcode.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the difference between [KeyboardController.hide] and [KeyboardController.dismiss]: only
 * `dismiss` takes editor focus away, and that focus is what makes a hidden keyboard come back —
 * the window restores the IME for whatever still holds it when the app returns from the background.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardControllerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var keyboard: KeyboardController
    private var editorFocused = false

    /**
     * Renders a focused text field with one non-editor focusable beside it, and captures the
     * [KeyboardController] alongside them.
     *
     * The second focusable is not padding. `clearFocus` hands focus to the next candidate rather
     * than leaving the tree with none, so a tree whose only focusable is the editor takes focus
     * straight back — an outcome no real screen produces, every one of them having buttons.
     */
    private fun focusedEditor() {
        composeTestRule.setContent {
            keyboard = rememberKeyboardController()
            val editor = remember { FocusRequester() }
            Column {
                Box(Modifier.size(20.dp).focusable())
                BasicTextField(
                    state = rememberTextFieldState(),
                    modifier = Modifier
                        .focusRequester(editor)
                        .onFocusChanged { editorFocused = it.isFocused },
                )
            }
            LaunchedEffect(Unit) { editor.requestFocus() }
        }
        composeTestRule.waitForIdle()
        assertTrue(editorFocused, "editor should start focused")
    }

    @Test
    fun `dismiss takes focus off the editor`() {
        focusedEditor()

        composeTestRule.runOnUiThread { keyboard.dismiss() }
        composeTestRule.waitForIdle()

        assertFalse(editorFocused)
    }

    @Test
    fun `hide leaves editor focus intact`() {
        focusedEditor()

        composeTestRule.runOnUiThread { keyboard.hide() }
        composeTestRule.waitForIdle()

        // The gap dismiss() exists to close: the IME goes down but the field stays focused, so the
        // platform has something to restore it onto.
        assertTrue(editorFocused)
    }
}
