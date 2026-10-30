package com.flipcash.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a tab home take the whole screen without leaving its route.
 *
 * The v2 tab bar is root chrome hoisted above the nav content, so it normally hides only when the
 * top route stops being a tab home. A screen that expands something to full screen *in place* — the
 * You tab's tip card — never changes route, so it has to say so.
 *
 * Requests are counted rather than latched to a boolean so overlapping callers can't uncover the
 * bar from under one another.
 */
@Stable
class TabBarVisibilityController {
    private var requests by mutableStateOf(0)

    val isHidden: Boolean get() = requests > 0

    fun hide() {
        requests++
    }

    fun release() {
        requests = (requests - 1).coerceAtLeast(0)
    }
}

val LocalTabBarVisibility = staticCompositionLocalOf { TabBarVisibilityController() }

/** Hides the tab bar for as long as [hidden] holds and this composable stays in the tree. */
@Composable
fun HideTabBar(hidden: Boolean) {
    val controller = LocalTabBarVisibility.current
    DisposableEffect(controller, hidden) {
        if (hidden) controller.hide()
        onDispose { if (hidden) controller.release() }
    }
}
