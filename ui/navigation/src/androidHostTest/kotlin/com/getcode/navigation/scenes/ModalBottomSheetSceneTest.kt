package com.getcode.navigation.scenes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.AppHome
import com.getcode.navigation.DemoSheet
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.navigation.scrim.ScrimController
import com.getcode.navigation.testNavigator
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

private const val SheetContentTag = "sheet-content"
private const val BaseContentTag = "base-content"

/**
 * [UnstyledBottomSheet] has no dismissal callback, so the scene has to notice the sheet settling
 * back at [SheetDetent.Hidden] itself. If it doesn't, a drag-dismiss leaves the (now invisible)
 * sheet entry on the backstack — swallowing touches through its full-screen scrim and keeping
 * app chrome that hides for sheets (the v2 tab bar) hidden until the next tap pops it.
 */
@RunWith(RobolectricTestRunner::class)
class ModalBottomSheetSceneTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var backCount = 0

    private fun showSheet() {
        backCount = 0
        val navigator = testNavigator(AppHome, DemoSheet)
        val baseEntry = NavEntry<NavKey>(key = AppHome) {
            Box(Modifier.fillMaxSize().testTag(BaseContentTag))
        }
        val sheetEntry = NavEntry<NavKey>(key = DemoSheet) {
            Box(Modifier.fillMaxSize().testTag(SheetContentTag))
        }
        val scene = ModalBottomSheetScene(
            key = DemoSheet,
            previousEntries = listOf(baseEntry),
            overlaidEntries = listOf(baseEntry),
            entry = sheetEntry,
            sheetProperties = BottomSheetProperties(),
            onBack = { backCount++ },
            metadata = emptyMap(),
            navResultStore = EmptyCodeNavigator.resultStore,
            lastNavKey = { AppHome },
        )

        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalCodeNavigator provides navigator,
                    LocalScrimController provides ScrimController(),
                ) {
                    scene.content()
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `an open sheet does not pop its entry`() {
        showSheet()

        assertEquals(0, backCount, "sheet popped its entry without being dismissed")
    }

    @Test
    fun `dragging the sheet closed pops its entry`() {
        showSheet()

        composeTestRule.onNodeWithTag(SheetContentTag).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertEquals(1, backCount, "drag-to-dismiss did not pop the sheet entry")
    }
}
