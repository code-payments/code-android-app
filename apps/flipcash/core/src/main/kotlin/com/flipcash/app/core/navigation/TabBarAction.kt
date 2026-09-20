package com.flipcash.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * A control published by [tab]'s home for the tab bar to draw beside the pill.
 *
 * The owning tab rides along because composition lifetime is the wrong clock for this. A tab press
 * swaps the backstack, but the outgoing screen stays composed until the route transition finishes,
 * so waiting for its dispose leaves the control on the bar for a beat after the indicator has
 * already moved. [tab] changes on the press itself, so the bar can start shrinking in step with it.
 */
@Immutable
class TabBarActionEntry(
    val tab: NavBarButton,
    val content: @Composable (Modifier) -> Unit,
)

/**
 * Lets a tab home hang one control off the end of the tab bar's row, beside the pill.
 *
 * The bar is root chrome and knows nothing about any screen, so the screen publishes here and the
 * bar renders what it finds. The control is handed a [Modifier] that already carries the pill's own
 * size and frosted fill — the same chain, not a copy — so the two cannot drift apart, and the
 * screen supplies only the click and the glyph. This is the same slot shape as the bar's `avatar`.
 *
 * Holding a `@Composable` lambda in state is deliberate: the control is composed in the *bar's*
 * composition, which is what puts it in the bar's row and lets it share the bar's haze source.
 */
@Stable
class TabBarActionController {
    var published: TabBarActionEntry? by mutableStateOf(null)
        private set

    fun set(entry: TabBarActionEntry) {
        published = entry
    }

    /**
     * Clears [entry] only if it is still the published one. Two screens overlap while a route
     * transition runs, so the outgoing screen's dispose lands *after* the incoming screen has
     * published; an unconditional clear would blank the incoming screen's control.
     */
    fun clear(entry: TabBarActionEntry) {
        if (published === entry) published = null
    }

    /** The control to draw while [selectedTab] is the open tab, or null if that tab published none. */
    fun actionFor(selectedTab: NavBarButton): (@Composable (Modifier) -> Unit)? =
        published?.takeIf { it.tab == selectedTab }?.content
}

val LocalTabBarAction = staticCompositionLocalOf { TabBarActionController() }

/** Publishes [content] into the tab bar's trailing slot for as long as [tab] is the open tab. */
@Composable
fun TabBarAction(tab: NavBarButton, content: @Composable (Modifier) -> Unit) {
    val controller = LocalTabBarAction.current
    // The caller's lambda is a fresh instance on every recomposition. Publishing that directly would
    // re-enter the DisposableEffect each time, so publish one stable entry and let it read through
    // to the latest content.
    val latest by rememberUpdatedState(content)
    val entry = remember(tab) { TabBarActionEntry(tab) { modifier -> latest(modifier) } }
    DisposableEffect(controller, entry) {
        controller.set(entry)
        onDispose { controller.clear(entry) }
    }
}
