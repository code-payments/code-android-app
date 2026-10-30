package com.flipcash.app.core.ui

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
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Long-pressing the You tab opens the account switcher (iOS `HomeTabView.handleLongPress(on:)`).
 * The caller passes a null callback while the gate is closed; then the tab must behave as before —
 * no haptic, and the hold still selects it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationBarLongPressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val haptics = mutableListOf<HapticFeedbackType>()
    private val clicks = mutableListOf<NavBarButton>()
    private var longClicks = 0

    private fun setBar(onYouTabLongClick: (() -> Unit)?) {
        composeRule.setContent {
            FlipcashPreview {
                CompositionLocalProvider(
                    LocalHapticFeedback provides object : HapticFeedback {
                        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                            haptics += hapticFeedbackType
                        }
                    },
                ) {
                    NavigationBar(
                        state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet),
                        onButtonClick = { clicks += it },
                        onYouTabLongClick = onYouTabLongClick,
                    )
                }
            }
        }
    }

    @Test
    fun `gate closed - long press on You fires no haptic and no switcher`() {
        setBar(onYouTabLongClick = null)

        composeRule.onNodeWithTag("nav_tipcard").performTouchInput { longClick() }

        assertEquals(0, longClicks)
        assertEquals(emptyList(), haptics)
        // Unchanged from before the long-press existed: a hold is just a slow tap.
        assertEquals(listOf(NavBarButton.TipCard), clicks)
    }

    @Test
    fun `gate open - long press on You fires the haptic and opens the switcher`() {
        setBar(onYouTabLongClick = { longClicks++ })

        composeRule.onNodeWithTag("nav_tipcard").performTouchInput { longClick() }

        assertEquals(1, longClicks)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics)
        assertEquals(emptyList(), clicks)
    }

    @Test
    fun `gate open - a tap on You still selects it`() {
        setBar(onYouTabLongClick = { longClicks++ })

        composeRule.onNodeWithTag("nav_tipcard").performClick()

        assertEquals(listOf(NavBarButton.TipCard), clicks)
        assertEquals(0, longClicks)
    }

    @Test
    fun `gate open - other tabs take no long press`() {
        setBar(onYouTabLongClick = { longClicks++ })

        listOf("nav_scanner", "nav_wallet", "nav_chats").forEach { tag ->
            composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        }

        assertEquals(0, longClicks)
        assertEquals(emptyList(), haptics)
        assertEquals(listOf(NavBarButton.Scanner, NavBarButton.Wallet, NavBarButton.Chats), clicks)
    }
}
