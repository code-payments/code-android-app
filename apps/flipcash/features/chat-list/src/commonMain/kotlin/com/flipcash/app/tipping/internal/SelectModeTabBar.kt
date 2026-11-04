package com.flipcash.app.tipping.internal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.HideTabBar
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.shared.chat.ui.media.rememberReducedMotion

/**
 * Select mode takes the tab bar away, as iOS's edit mode does, and gives its space to the list.
 *
 * Hides the hoisted bar for as long as [isSelecting] holds and returns the list's bottom inset,
 * which eases from the bar's height to zero and back. The bar slides on `AnimatedVisibility`'s
 * default slide spring, so the inset runs on the same spring and the last row follows the bar's
 * edge instead of jumping when the mode flips. With system animations off it snaps.
 */
@Composable
internal fun selectModeTabBarInset(isSelecting: Boolean): State<Dp> {
    HideTabBar(hidden = isSelecting)
    val barHeight = LocalTabBarPadding.current.calculateBottomPadding()
    val reducedMotion = rememberReducedMotion()
    return animateDpAsState(
        targetValue = if (isSelecting) 0.dp else barHeight,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Dp.VisibilityThreshold)
        },
        label = "select mode tab bar inset",
    )
}
