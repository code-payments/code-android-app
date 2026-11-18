package com.flipcash.app.core.ui.transitions

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A field asks for focus once its screen has finished sliding in, not on first composition, so
 * the keyboard does not rise under the slide. With `onlyOnce`, coming back to the screen leaves it
 * unfocused, as the onboarding phone, code and email steps did with `OnWindowFocusedRequester`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestFocusWhenSettledTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var screen by mutableStateOf(BLANK)
    private var windowFocused by mutableStateOf(true)

    private val windowInfo = object : WindowInfo {
        override val isWindowFocused: Boolean get() = windowFocused
    }

    /** [BLANK] has no field; [FIELD] has one that asks for focus with [RequestFocusWhenSettled]. */
    private fun setScreens(onlyOnce: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                val saveableState = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        slideInHorizontally(tween(SLIDE_MS)) { it } togetherWith
                            slideOutHorizontally(tween(SLIDE_MS)) { -it }
                    },
                ) { current ->
                    saveableState.SaveableStateProvider(current) {
                        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                            Box(Modifier.fillMaxSize()) {
                                if (current == FIELD) {
                                    val focusRequester = remember { FocusRequester() }
                                    BasicTextField(
                                        value = "",
                                        onValueChange = {},
                                        modifier = Modifier
                                            .testTag(FIELD)
                                            .focusRequester(focusRequester),
                                    )
                                    RequestFocusWhenSettled(focusRequester, onlyOnce)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Writes on the UI thread and lets the write reach the recomposer, without advancing time. */
    private fun onUi(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    private fun navigateTo(target: String) {
        onUi { screen = target }
        composeRule.mainClock.advanceTimeBy(SLIDE_MS + 200L)
        composeRule.waitForIdle()
    }

    @Test
    fun `focus waits for the slide to finish`() {
        setScreens(onlyOnce = false)
        composeRule.mainClock.autoAdvance = false

        onUi { screen = FIELD }
        composeRule.mainClock.advanceTimeBy(SLIDE_MS / 2L)
        composeRule.onNodeWithTag(FIELD).assertIsNotFocused()

        composeRule.mainClock.advanceTimeBy(SLIDE_MS.toLong())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun `focus waits for the window to have focus`() {
        windowFocused = false
        setScreens(onlyOnce = false)
        composeRule.mainClock.autoAdvance = false

        navigateTo(FIELD)
        composeRule.onNodeWithTag(FIELD).assertIsNotFocused()

        onUi { windowFocused = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun `onlyOnce - coming back to the screen leaves the field unfocused`() {
        setScreens(onlyOnce = true)
        composeRule.mainClock.autoAdvance = false

        navigateTo(FIELD)
        composeRule.onNodeWithTag(FIELD).assertIsFocused()

        navigateTo(BLANK)
        navigateTo(FIELD)
        composeRule.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun `by default - coming back to the screen focuses the field again`() {
        setScreens(onlyOnce = false)
        composeRule.mainClock.autoAdvance = false

        navigateTo(FIELD)
        composeRule.onNodeWithTag(FIELD).assertIsFocused()

        navigateTo(BLANK)
        navigateTo(FIELD)
        composeRule.onNodeWithTag(FIELD).assertIsFocused()
    }

    private companion object {
        const val BLANK = "blank"
        const val FIELD = "field"
        const val SLIDE_MS = 300
    }
}
