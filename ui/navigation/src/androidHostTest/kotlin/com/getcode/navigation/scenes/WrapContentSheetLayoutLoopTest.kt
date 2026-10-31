package com.getcode.navigation.scenes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.AppHome
import com.getcode.navigation.DemoSheet
import com.getcode.navigation.NavMetadataKeys
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

/**
 * A wrap-content sheet whose content animates its height while the sheet is still opening.
 *
 * The app hosts every scene in a `SharedTransitionLayout`, a lookahead scope, so the sheet is
 * measured twice a frame. `AnimatedContent` with a [SizeTransform] reports its target height to
 * the lookahead pass and the animated height to the main pass, and compose-unstyled's `Sheet`
 * writes each into its state during measure. While the sheet moves, the two writes keep
 * invalidating each other and the frame never ends: the host Invite sheet's ANR, when a group's
 * Approve New Members loads as on just after the sheet appears.
 */
@RunWith(RobolectricTestRunner::class)
class WrapContentSheetLayoutLoopTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content that animates its height while the sheet opens does not spin layout`() {
        var tall by mutableStateOf(false)
        var measures = 0
        val sheetEntry = NavEntry<NavKey>(key = DemoSheet) {
            AnimatedContent(
                targetState = tall,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        // A spinning layout never returns to the test; fail out of it instead.
                        check(++measures < MeasureLimit) { "sheet layout did not settle" }
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using
                        SizeTransform(clip = false) { _, _ -> tween(300) }
                },
                label = "test",
            ) { isTall ->
                Box(Modifier.fillMaxWidth().height(if (isTall) 240.dp else 160.dp))
            }
        }
        val baseEntry = NavEntry<NavKey>(key = AppHome) { Box(Modifier.fillMaxSize()) }
        val scene = ModalBottomSheetScene(
            key = DemoSheet,
            previousEntries = listOf(baseEntry),
            overlaidEntries = listOf(baseEntry),
            entry = sheetEntry,
            sheetProperties = BottomSheetProperties(),
            onBack = {},
            metadata = mapOf(NavMetadataKeys.IsWrapContentSheet.key to true),
            navResultStore = EmptyCodeNavigator.resultStore,
            lastNavKey = { AppHome },
        )

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalCodeNavigator provides testNavigator(AppHome, DemoSheet),
                    LocalScrimController provides ScrimController(),
                ) {
                    SharedTransitionLayout { scene.content() }
                }
            }
        }

        // Partway up, the content learns it is taller, as the host sheet does when the group's
        // Approve New Members setting arrives.
        composeTestRule.mainClock.advanceTimeBy(64)
        tall = true
        repeat(60) { composeTestRule.mainClock.advanceTimeByFrame() }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val MeasureLimit = 5_000
    }
}
