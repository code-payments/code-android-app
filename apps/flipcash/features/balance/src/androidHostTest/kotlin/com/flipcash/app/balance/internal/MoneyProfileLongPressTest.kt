package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.flipcash.app.core.AppRoute
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The group money shell has no You tab, so the Money avatar that opens You also takes the You
 * tab's long press: the account switcher. The app passes a null callback while the switcher's gate
 * is closed; then a hold on the avatar does nothing, with no haptic, and a tap still opens You.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyProfileLongPressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val events = mutableListOf<WalletViewModel.Event>()
    private var longClicks = 0

    private fun setBar(onProfileLongClick: (() -> Unit)?) {
        composeRule.setContent {
            FlipcashPreview {
                CompositionLocalProvider(
                    LocalHapticFeedback provides object : HapticFeedback {
                        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                            haptics += hapticFeedbackType
                        }
                    },
                ) {
                    MoneyTitleBar(
                        profileAvatar = {},
                        dispatchEvent = { events += it },
                        onProfileLongClick = onProfileLongClick,
                    )
                }
            }
        }
    }

    @Test
    fun `gate open - long press on the avatar fires the haptic and opens the switcher`() {
        setBar(onProfileLongClick = { longClicks++ })

        composeRule.onNodeWithTag("money_profile").performTouchInput { longClick() }

        assertEquals(1, longClicks)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics)
        assertEquals(emptyList(), events)
    }

    @Test
    fun `gate closed - long press on the avatar does nothing`() {
        setBar(onProfileLongClick = null)

        composeRule.onNodeWithTag("money_profile").performTouchInput { longClick() }

        assertEquals(0, longClicks)
        assertEquals(emptyList(), haptics)
        // Not a slow tap: a hold does not open You.
        assertEquals(emptyList(), events)
    }

    @Test
    fun `gate closed - a tap on the avatar still opens You`() {
        setBar(onProfileLongClick = null)

        composeRule.onNodeWithTag("money_profile").performClick()

        assertEquals(listOf<WalletViewModel.Event>(WalletViewModel.Event.OpenScreen(AppRoute.Tabs.Menu)), events)
        assertEquals(emptyList(), haptics)
    }

    @Test
    fun `gate open - a tap on the avatar still opens You`() {
        setBar(onProfileLongClick = { longClicks++ })

        composeRule.onNodeWithTag("money_profile").performClick()

        assertEquals(listOf<WalletViewModel.Event>(WalletViewModel.Event.OpenScreen(AppRoute.Tabs.Menu)), events)
        assertEquals(0, longClicks)
        assertEquals(emptyList(), haptics)
    }
}
