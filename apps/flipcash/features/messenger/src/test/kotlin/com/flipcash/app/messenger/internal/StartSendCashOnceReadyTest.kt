package com.flipcash.app.messenger.internal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The chat opened by a profile's Send Cash starts the send once, when the fee state is known, and
 * never again for that chat: not when readiness flickers, and not when the screen is restored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartSendCashOnceReadyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `waits for readiness, then starts once`() {
        var ready by mutableStateOf(false)
        var starts = 0
        composeRule.setContent {
            StartSendCashOnceReady(requested = true, ready = ready) { starts++ }
        }
        composeRule.waitForIdle()
        assertEquals(0, starts)

        ready = true
        composeRule.waitForIdle()
        assertEquals(1, starts)

        // The participant or fee changing under an open chat, then settling again.
        ready = false
        composeRule.waitForIdle()
        ready = true
        composeRule.waitForIdle()
        assertEquals(1, starts)
    }

    @Test
    fun `a restored screen doesn't start it again`() {
        val restoration = StateRestorationTester(composeRule)
        var starts = 0
        restoration.setContent {
            StartSendCashOnceReady(requested = true, ready = true) { starts++ }
        }
        composeRule.waitForIdle()
        assertEquals(1, starts)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(1, starts)
    }

    @Test
    fun `an ordinary open never starts it`() {
        var starts = 0
        composeRule.setContent {
            StartSendCashOnceReady(requested = false, ready = true) { starts++ }
        }
        composeRule.waitForIdle()
        assertEquals(0, starts)
    }
}
